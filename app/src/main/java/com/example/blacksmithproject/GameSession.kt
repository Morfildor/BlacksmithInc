package com.example.blacksmithproject

import com.example.blacksmithproject.data.GameRepository
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.data.StoredRows
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.RunId
import com.tinyblacksmith.core.model.UpgradeId
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        data class MoveCursor(val cursor: DayCursor) : Op
    }
    sealed interface Result {
        data class Done(val accepted: CommandOutcome.Accepted? = null) : Result
        data class Rejected(val error: GameError) : Result
        data object Stale : Result               // issued against a run that is no longer current: nothing written
        data object DayNotWatched : Result       // a planning command while the counter or aftermath is unwatched
        data class Failed(val failure: SaveFailure) : Result
        data class EngineFault(val message: String) : Result      // the engine threw: nothing written, never retried
    }
    sealed interface Status {
        data object Idle : Status
        data class Working(val op: Op) : Status
        data class Failed(val op: Op, val failure: SaveFailure) : Status
    }

    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    private val _status = MutableStateFlow<Status>(Status.Idle)
    /** The run row as stored, so a legacy-only commit puts the ended run back byte for byte. Touched only under the lock. */
    private var runText: String? = null

    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()      // null until load() succeeds
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Reads the rows and publishes them. Under the lock, so it can never publish rows older than a commit. */
    suspend fun load(): Result = mutex.withLock {
        val rows = try { repo.load() } catch (e: SaveFailure) { return Result.Failed(e) }
        val decoded = try { withContext(compute) { decode(rows) } } catch (e: SaveFailure) { return Result.Failed(e) }
        runText = rows.run
        _snapshot.value = decoded
        Result.Done()
    }

    /** An op issued before the first successful load answers Stale. */
    suspend fun run(op: Op): Result = mutex.withLock {
        val snap = _snapshot.value ?: return Result.Stale
        if (op is Op.MoveCursor) return moveCursor(snap, op.cursor)
        _status.value = Status.Working(op)
        var result: Result? = null
        try {
            result = execute(snap, op)
        } finally {
            _status.value = (result as? Result.Failed)?.let { Status.Failed(op, it.failure) } ?: Status.Idle
        }
        result
    }

    suspend fun retry(): Result = TODO("T1.2: re-run the op held in Status.Failed, or the load when nothing is loaded")

    suspend fun startOverKeepingBackup(): Result = TODO("T1.2: quarantine(\"run\"), then publish Snapshot(null, legacy, null)")

    /**
     * T1.2 seam: today any row that does not decode is Corrupt. The version check (SaveFailure.Newer) and
     * Compatibility.admit (SaveFailure.Incompatible) belong here. A cursor that cannot be read is no cursor.
     */
    private fun decode(rows: StoredRows): Snapshot {
        val legacy = rows.legacy?.let { text -> readRow("legacy") { SaveCodec.decodeLegacy(text) } } ?: LegacyProfile()
        val run = rows.run?.let { text -> readRow("run") { SaveCodec.decodeRun(text) } }
        val cursor = rows.cursor?.let { text -> runCatching { DayCursor.decode(text) }.getOrNull() }
        return Snapshot(run, legacy, cursor)
    }

    private inline fun <T> readRow(key: String, block: () -> T): T = try { block() } catch (e: Exception) { throw SaveFailure.Corrupt(key, e) }

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
                    if (rows == null || rows.run != step.runText || rows.legacy != step.legacyText) return@withContext Result.Failed(e)
                }
                runText = step.runText
                _snapshot.value = Snapshot(step.run, step.legacy, snap.cursor)
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
