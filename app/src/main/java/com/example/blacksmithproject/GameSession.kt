package com.example.blacksmithproject

import com.example.blacksmithproject.data.GameRepository
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.data.StoredRows
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Compatibility
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.RunId
import com.tinyblacksmith.core.model.UpgradeId
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.persistence.SaveEnvelope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one operation boundary between the UI and the save (plan 6.1). Every entry point holds the same lock across
 * read, compute, write and publish, so two taps can never compute from the same state, and nothing is published
 * before its save has completed. Callers pass intent (a command, an upgrade ID) and the run it was issued against.
 */
class GameSession(
    private val engine: GameEngine,
    private val repo: GameRepository,
    private val compute: CoroutineDispatcher = Dispatchers.Default,   // engine calls and JSON encoding; the session never names Dispatchers.IO
) {
    /** Exactly what is on disk. The UI renders this and nothing newer. */
    data class Snapshot(val run: GameState?, val legacy: LegacyProfile, val cursor: DayCursor?)

    sealed interface Op {
        data class Dispatch(val command: Command, val runId: RunId) : Op
        data class Claim(val runId: RunId) : Op
        data class BuyUpgrade(val upgradeId: UpgradeId, val runId: RunId?) : Op
        data class BeginEra(val seed: Long, val afterRunId: RunId?) : Op
        /** Discards a run that has not ended. Nothing is claimed: the legacy row stays as it was before the run. */
        data class Abandon(val runId: RunId) : Op
        data class MoveCursor(val cursor: DayCursor) : Op
    }
    sealed interface Result {
        data class Done(val accepted: CommandOutcome.Accepted? = null) : Result
        data class Rejected(val error: GameError) : Result
        data object Stale : Result               // issued against a run that is no longer current: nothing written
        data object DayNotWatched : Result       // a planning command while the counter or aftermath is unwatched
        /** [unconfirmed]: the save failed and so did the read that would have said whether it landed. */
        data class Failed(val failure: SaveFailure, val unconfirmed: Boolean = false) : Result
        data class EngineFault(val message: String) : Result      // the engine threw: nothing written, never retried
    }
    sealed interface Status {
        data object Idle : Status
        data class Working(val op: Op) : Status
        data class Failed(val op: Op, val failure: SaveFailure, val unconfirmed: Boolean = false) : Status
    }

    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    private val _status = MutableStateFlow<Status>(Status.Idle)
    /** The run row as stored, so a legacy-only commit puts the ended run back byte for byte. Touched only under the lock. */
    private var runText: String? = null

    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()      // null until load() succeeds
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Reads the rows, admits the run and publishes them. Under the lock, so it can never publish rows older than a commit. */
    suspend fun load(): Result = mutex.withLock { loadLocked() }

    /** An op issued before the first successful load answers Stale. */
    suspend fun run(op: Op): Result = mutex.withLock { runLocked(op) }

    /** Reads the save again when nothing is loaded; otherwise re-runs the op held in Status.Failed from the same snapshot. */
    suspend fun retry(): Result = mutex.withLock {
        if (_snapshot.value == null) return loadLocked()
        val failed = _status.value as? Status.Failed ?: return Result.Done()
        runLocked(failed.op)
    }

    /** "Keep working": the failed op is dropped; the snapshot is still the last saved state. */
    fun dismissFailure() { _status.update { if (it is Status.Failed) Status.Idle else it } }

    /**
     * The way out of a save that cannot be loaded: only what this build cannot read is moved aside (never deleted),
     * a row under a backup key, a damaged file under a backup name. A run that loads is never set aside: when the
     * legacy row beside it is the unreadable one, that row is rebuilt from the copy the run carries (as of the last
     * saved action of the run, so an ended run comes back unclaimed and is claimed again for the same award).
     * Then the store is read again.
     */
    suspend fun startOverKeepingBackup(): Result = mutex.withLock {
        if (_snapshot.value != null) return Result.Done()      // only ever the way out of a failed load
        try {
            val rows = try { repo.load() } catch (_: SaveFailure.FileDamaged) { repo.quarantineFile(); return loadLocked() }
            val run = withContext(compute) { soundRun(rows) }
            if (rows.legacy != null && runCatching { withContext(compute) { decodeLegacy(rows.legacy) } }.isFailure) {
                repo.quarantine("legacy")
                if (run != null) repo.commit(rows.run, SaveCodec.encodeLegacy(run.legacy))
            }
            if (rows.run != null && run == null) {
                repo.quarantine("run")
                repo.saveCursor(null)
            }
        } catch (e: SaveFailure) {
            return Result.Failed(e)
        }
        loadLocked()
    }

    private suspend fun loadLocked(): Result {
        val rows = try { repo.load() } catch (e: SaveFailure) { return Result.Failed(e) }
        val decoded = try { withContext(compute) { decode(rows) } } catch (e: SaveFailure) { return Result.Failed(e) }
        runText = rows.run
        _snapshot.value = decoded
        return Result.Done()
    }

    private suspend fun runLocked(op: Op): Result {
        val snap = _snapshot.value ?: return Result.Stale
        // A cursor only ever names the stored last day: one issued against an earlier day arrives too late to mean anything.
        if (op is Op.MoveCursor) return if (snap.run?.lastResolution?.commandId?.value == op.cursor.commandId) moveCursor(snap, op.cursor) else Result.Stale
        _status.value = Status.Working(op)
        var result: Result? = null
        try {
            result = execute(snap, op)
        } finally {
            _status.value = (result as? Result.Failed)?.let { Status.Failed(op, it.failure, it.unconfirmed) } ?: Status.Idle
        }
        return result
    }

    /**
     * The legacy row is read first, so a bad run never hides a bad legacy; its failure says whether the run beside it
     * loads. The published run is the admitted one (versions stamped to this build); the stored bytes are left alone
     * until the next accepted command. A cursor that cannot be read is no cursor.
     */
    private fun decode(rows: StoredRows): Snapshot {
        val legacy = try {
            // A run with no legacy row beside it (a rebuild that was cut short) reads the copy the run carries.
            rows.legacy?.let { decodeLegacy(it) } ?: soundRun(rows)?.legacy ?: LegacyProfile()
        } catch (e: SaveFailure) {
            throw when {
                soundRun(rows) == null -> e
                e is SaveFailure.Corrupt -> SaveFailure.Corrupt(e.key, e.cause ?: e, runSound = true)
                e is SaveFailure.Newer -> SaveFailure.Newer(e.key, e.found, e.supported, runSound = true)
                else -> e
            }
        }
        val run = rows.run?.let { admitRun(it) }
        val cursor = rows.cursor?.let { text -> runCatching { DayCursor.decode(text) }.getOrNull() }
        return Snapshot(run, legacy, cursor)
    }

    private fun admitRun(text: String): GameState =
        when (val admitted = Compatibility.admit(readRow("run", text) { SaveCodec.decodeRun(text) }, engine.content, engine.config)) {
            is Compatibility.Result.Admitted -> admitted.state
            is Compatibility.Result.Unsupported -> throw SaveFailure.Incompatible(admitted.problems)
        }

    /** The stored run when it decodes and is admitted, else null. */
    private fun soundRun(rows: StoredRows): GameState? = rows.run?.let { text -> try { admitRun(text) } catch (_: SaveFailure) { null } }

    private fun decodeLegacy(text: String): LegacyProfile = readRow("legacy", text) { SaveCodec.decodeLegacy(text) }

    /** Corrupt: the envelope or its payload does not decode. Newer: the envelope is sound and from a later schema. */
    private inline fun <T> readRow(key: String, text: String, block: () -> T): T {
        val found = try { SaveCodec.json.decodeFromString(SaveEnvelope.serializer(), text).schemaVersion } catch (e: Exception) { throw SaveFailure.Corrupt(key, e) }
        if (found > SaveCodec.SCHEMA_VERSION) throw SaveFailure.Newer(key, found, SaveCodec.SCHEMA_VERSION)
        return try { block() } catch (e: Exception) { throw SaveFailure.Corrupt(key, e) }
    }

    private sealed interface Step {
        class Answer(val result: Result) : Step
        class Write(val run: GameState?, val runText: String?, val legacy: LegacyProfile, val legacyText: String, val accepted: CommandOutcome.Accepted? = null) : Step
    }

    private suspend fun execute(snap: Snapshot, op: Op): Result {
        val step = try {
            withContext(compute) { plan(snap, op) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.EngineFault(e.message ?: e.toString())
        }
        return when (step) {
            is Step.Answer -> step.result
            is Step.Write -> withContext(NonCancellable) {
                try {
                    repo.commit(step.runText, step.legacyText)
                } catch (e: SaveFailure) {
                    // The transaction may have committed before the failure surfaced: one confirmation read decides.
                    val rows = try { repo.load() } catch (_: SaveFailure) { null }
                    if (rows == null || rows.run != step.runText || rows.legacy != step.legacyText) return@withContext Result.Failed(e, unconfirmed = rows == null)
                }
                runText = step.runText
                // A discarded run takes its day position with it, so a later run with the same ID starts unwatched.
                if (step.run == null) try { repo.saveCursor(null) } catch (_: SaveFailure) { }
                _snapshot.value = Snapshot(step.run, step.legacy, if (step.run == null) null else snap.cursor)
                Result.Done(step.accepted)
            }
        }
    }

    /** Pure: decides what the op does to the snapshot it was given. Runs on [compute]. */
    private fun plan(snap: Snapshot, op: Op): Step {
        val run = snap.run
        val claimed = run != null && run.runId.value in snap.legacy.claimedRunIds
        val result = when (op) {
            is Op.Dispatch -> when {
                run == null || run.runId != op.runId -> Result.Stale
                claimed -> Result.Rejected(GameError.RunEnded)
                !planningOpen(pending(run, snap.legacy, snap.cursor), op.command) -> Result.DayNotWatched
                else -> when (val out = engine.handle(run, op.command)) {
                    is CommandOutcome.Rejected -> Result.Rejected(out.error)
                    // An End Day replayed by its command ID returns the state it was given: nothing to write.
                    is CommandOutcome.Accepted -> if (out.state == run) Result.Done(out) else
                        return Step.Write(out.state, SaveCodec.encodeRun(out.state), out.state.legacy, SaveCodec.encodeLegacy(out.state.legacy), out)
                }
            }
            is Op.Claim -> when {
                run == null || run.runId != op.runId -> Result.Stale
                claimed -> Result.Rejected(GameError.AlreadyClaimed(run.runId))
                !run.isEnded || pending(run, snap.legacy, snap.cursor) != DayCursor.Stage.DONE -> Result.Rejected(GameError.RunNotEnded)
                else -> return legacyStep(engine.claimLegacy(snap.legacy, engine.closeRun(run)), run)
            }
            is Op.BuyUpgrade -> when {
                run?.runId != op.runId -> Result.Stale
                run != null && !(run.isEnded && claimed) -> Result.Rejected(GameError.RunNotEnded)
                else -> return legacyStep(engine.purchaseUpgrade(snap.legacy, op.upgradeId), run)
            }
            is Op.BeginEra -> when {
                run?.runId != op.afterRunId -> Result.Stale
                run != null && !(run.isEnded && claimed) -> Result.Rejected(GameError.RunNotEnded)
                else -> {
                    val next = engine.newRun(snap.legacy, op.seed)
                    return Step.Write(next, SaveCodec.encodeRun(next), snap.legacy, SaveCodec.encodeLegacy(snap.legacy))
                }
            }
            is Op.Abandon -> when {
                run == null || run.runId != op.runId -> Result.Stale
                run.isEnded -> Result.Rejected(GameError.RunEnded)
                else -> return Step.Write(null, null, snap.legacy, SaveCodec.encodeLegacy(snap.legacy))
            }
            is Op.MoveCursor -> error("MoveCursor never reaches the planner")
        }
        return Step.Answer(result)
    }

    /** A legacy change keeps the run row exactly as stored (the ended run is never deleted). */
    private fun legacyStep(outcome: LegacyOutcome, run: GameState?): Step = when (outcome) {
        is LegacyOutcome.Updated -> Step.Write(run, runText, outcome.legacy, SaveCodec.encodeLegacy(outcome.legacy))
        is LegacyOutcome.Rejected -> Step.Answer(Result.Rejected(outcome.error))
    }

    private fun planningOpen(stage: DayCursor.Stage, command: Command): Boolean = when (stage) {
        DayCursor.Stage.COUNTER, DayCursor.Stage.AFTERMATH -> false
        DayCursor.Stage.TOMORROW -> command is Command.ChooseBlessing
        DayCursor.Stage.DONE -> true
    }

    /** A lost cursor write only repeats presentation, so the snapshot moves even when the row could not be written. */
    private suspend fun moveCursor(snap: Snapshot, cursor: DayCursor): Result = withContext(NonCancellable) {
        try { repo.saveCursor(cursor.encode()) } catch (_: SaveFailure) { }
        _snapshot.value = snap.copy(cursor = cursor)
        Result.Done()
    }

    companion object {
        /** Where the presentation of the last resolved day stands. Derived; the cursor row alone is never trusted. */
        fun pending(run: GameState?, legacy: LegacyProfile, cursor: DayCursor?): DayCursor.Stage {
            val last = run?.lastResolution ?: return DayCursor.Stage.DONE
            return when {
                run.isEnded && run.runId.value in legacy.claimedRunIds -> DayCursor.Stage.DONE
                cursor == null || cursor.commandId != last.commandId.value -> DayCursor.Stage.COUNTER
                else -> cursor.stage
            }
        }
    }
}
