package com.example.blacksmithproject

import com.example.blacksmithproject.GameSession.Op
import com.example.blacksmithproject.GameSession.Result
import com.example.blacksmithproject.GameSession.Status
import com.example.blacksmithproject.data.SaveFailure
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.persistence.SaveEnvelope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/** Plan 6.1 rules 5 to 7 and the load mapping: a save that cannot be read or written is reported, never replaced. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameSessionFailureTest {
    private val engine = GameEngine()
    private val repos = mutableListOf<FakeGameRepository>()

    /** Releases every held commit on the way out: the session commits under NonCancellable, so a failed assertion would otherwise hang. */
    private fun sessionTest(body: suspend TestScope.() -> Unit) = runTest {
        try { body() } finally { repos.forEach { it.releaseAll() } }
    }

    private fun TestScope.session(repo: FakeGameRepository) = GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler))

    private fun repo(run: String?, legacy: String?) = FakeGameRepository(run, legacy).also { repos += it }

    private fun liveRepo(run: GameState = engine.newRun(LegacyProfile(), 42L)) = repo(SaveCodec.encodeRun(run), SaveCodec.encodeLegacy(run.legacy))

    private fun endDay(s: GameState) = Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))

    private val veteran = LegacyProfile(points = 12)

    private suspend fun GameSession.loadFailure(): SaveFailure = (load() as Result.Failed).failure

    @Test
    fun corruptRunKeepsItsBytesAndReportsCorrupt() = sessionTest {
        val garbage = "{\"schemaVersion\":1,\"payload\":\"{ not a run\"}"
        for (text in listOf(garbage, "not json at all", "")) {
            val repo = repo(text, SaveCodec.encodeLegacy(veteran))
            val session = session(repo)
            val failure = session.loadFailure()
            assertTrue("got $failure", failure is SaveFailure.Corrupt && failure.key == "run")
            assertEquals("the row is exactly as it was", text, repo.run)
            assertEquals(SaveCodec.encodeLegacy(veteran), repo.legacy)
            assertEquals(0, repo.commitCount)
            assertTrue(repo.quarantined.isEmpty())
            assertNull("nothing is published from a save that failed to load", session.snapshot.value)
            assertEquals("a planning command cannot run on nothing", Result.Stale, session.run(Op.BeginEra(1L, null)))
        }
        // A legacy row that cannot be read is reported as such, even beside a sound run.
        val start = engine.newRun(LegacyProfile(), 42L)
        val failure = session(repo(SaveCodec.encodeRun(start), garbage)).loadFailure()
        assertTrue(failure is SaveFailure.Corrupt && failure.key == "legacy")
        assertTrue("and says the run beside it loads", (failure as SaveFailure.Corrupt).runSound)
        val alone = session(repo("not a run either", garbage)).loadFailure()
        assertTrue(alone is SaveFailure.Corrupt && alone.key == "legacy" && !alone.runSound)
    }

    @Test
    fun newerSchemaIsReportedAsNewer() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        // A later schema whose payload this build could not make sense of anyway.
        val newer = SaveCodec.json.encodeToString(SaveEnvelope.serializer(), SaveEnvelope(SaveCodec.SCHEMA_VERSION + 1, "{\"shape\":\"unknown\"}"))
        val repo = repo(newer, SaveCodec.encodeLegacy(start.legacy))
        val failure = session(repo).loadFailure()
        assertTrue("got $failure", failure is SaveFailure.Newer)
        failure as SaveFailure.Newer
        assertEquals(Triple("run", SaveCodec.SCHEMA_VERSION + 1, SaveCodec.SCHEMA_VERSION), Triple(failure.key, failure.found, failure.supported))
        assertEquals(newer, repo.run)

        val newerLegacy = session(repo(SaveCodec.encodeRun(start), newer)).loadFailure()
        assertTrue(newerLegacy is SaveFailure.Newer && newerLegacy.key == "legacy")
    }

    @Test
    fun incompatibleRunIsReportedWithItsProblems() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val fromLaterRules = SaveCodec.encodeRun(start.copy(rulesVersion = GameEngine.RULES_VERSION + 1, gold = -5))
        val repo = repo(fromLaterRules, SaveCodec.encodeLegacy(veteran))
        val session = session(repo)
        val failure = session.loadFailure()
        assertTrue("got $failure", failure is SaveFailure.Incompatible)
        val problems = (failure as SaveFailure.Incompatible).problems
        assertTrue("$problems", problems.any { it.startsWith("Run uses rules") } && problems.any { it.startsWith("Negative gold") })
        assertEquals(fromLaterRules, repo.run)
        assertNull(session.snapshot.value)

        // A run from older rules and an untracked balance version is admitted and stamped forward; its bytes stay as stored.
        val older = SaveCodec.encodeRun(start.copy(rulesVersion = 0, balanceVersion = 0))
        val oldRepo = repo(older, SaveCodec.encodeLegacy(start.legacy))
        val oldSession = session(oldRepo)
        assertTrue(oldSession.load() is Result.Done)
        val run = oldSession.snapshot.value!!.run!!
        assertEquals(Triple(GameEngine.RULES_VERSION, engine.content.version, engine.config.version), Triple(run.rulesVersion, run.contentVersion, run.balanceVersion))
        assertEquals(older, oldRepo.run)
        assertEquals(0, oldRepo.commitCount)
    }

    /** The upgrade path: a schema 1, rules 1 save (the 0.6.0 fixture kept by core) loads, and the engine accepts its next day. */
    @Test
    fun aSaveFromTheOlderRulesIsAdmittedAndAcceptsAnEndDay() = sessionTest {
        val text = File("../core/src/test/resources/saves/v1_forced_seed4242_day61.json").readText()
        val stored = SaveCodec.decodeRun(text)
        assertTrue("the fixture predates this build's rules", stored.rulesVersion < GameEngine.RULES_VERSION)
        val repo = repo(text, SaveCodec.encodeLegacy(stored.legacy))
        val session = session(repo)
        assertTrue(session.load() is Result.Done)
        val run = session.snapshot.value!!.run!!
        assertEquals(GameEngine.RULES_VERSION, run.rulesVersion)
        assertEquals("loading rewrites nothing", text, repo.run)

        session.run(Op.MoveCursor(DayCursor(run.lastResolution!!.commandId.value, DayCursor.Stage.DONE)))
        val result = session.run(Op.Dispatch(endDay(run), run.runId))
        assertTrue("got $result", result is Result.Done && result.accepted?.resolution != null)
        assertEquals(run.day + 1, SaveCodec.decodeRun(repo.run!!).day)
        assertEquals(GameEngine.RULES_VERSION, SaveCodec.decodeRun(repo.run!!).rulesVersion)
    }

    @Test
    fun failedCommitLeavesTheLastSnapshotAndRetrySucceeds() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val runRow = repo.run
        val session = session(repo)
        session.load()
        val before = session.snapshot.value
        repo.failNextCommit = IOException("disk full")

        val op = Op.Dispatch(endDay(start), start.runId)
        val failed = session.run(op)
        assertTrue(failed is Result.Failed && failed.failure is SaveFailure.Io)
        assertEquals("the last saved state is still what is shown", before, session.snapshot.value)
        assertEquals(runRow, repo.run)
        assertEquals(Status.Failed(op, (failed as Result.Failed).failure), session.status.value)

        assertTrue(session.retry() is Result.Done)
        assertEquals(Status.Idle, session.status.value)
        assertEquals(2, session.snapshot.value?.run?.day)
        assertEquals(session.snapshot.value?.run, SaveCodec.decodeRun(repo.run!!))
        assertEquals(1, repo.commitCount)
        assertTrue("a second retry has nothing to run", session.retry().let { it is Result.Done && it.accepted == null })
        assertEquals(1, repo.commitCount)

        // "Keep working" drops the failed op instead.
        repo.failNextCommit = IOException("disk full")
        val next = session.snapshot.value!!.run!!
        session.run(Op.MoveCursor(DayCursor(next.lastResolution!!.commandId.value, DayCursor.Stage.DONE)))
        assertTrue(session.run(Op.Dispatch(endDay(next), next.runId)) is Result.Failed)
        session.dismissFailure()
        assertEquals(Status.Idle, session.status.value)
        assertEquals(2, session.snapshot.value?.run?.day)
    }

    @Test
    fun aCommitThatLandedBeforeFailingIsDetectedByTheConfirmationRead() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()
        val loads = repo.loadCount
        repo.failNextCommitAfterWriting = IOException("the error surfaced after the transaction committed")

        val result = session.run(Op.Dispatch(endDay(start), start.runId))
        assertTrue("got $result", result is Result.Done && result.accepted?.resolution != null)
        assertEquals("exactly one confirmation read", loads + 1, repo.loadCount)
        assertEquals(Status.Idle, session.status.value)
        assertEquals(SaveCodec.decodeRun(repo.run!!), session.snapshot.value?.run)
        assertEquals(2, session.snapshot.value?.run?.day)
        assertTrue("there is nothing left to retry, so the day cannot be played twice", session.retry().let { it is Result.Done && it.accepted == null })
        assertEquals(1, repo.commitCount)
    }

    /** Review m2: when the confirmation read fails too, the session does not know whether the save landed, and says so. */
    @Test
    fun aSaveThatCouldNotBeConfirmedIsReportedAsUnconfirmed() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()
        repo.failNextCommitAfterWriting = IOException("the error surfaced after the transaction committed")
        repo.failNextLoad = IOException("and the store did not answer the read either")

        val op = Op.Dispatch(endDay(start), start.runId)
        val failed = session.run(op) as Result.Failed
        assertTrue(failed.unconfirmed)
        assertEquals(Status.Failed(op, failed.failure, unconfirmed = true), session.status.value)
        assertEquals("what is shown is still the day before", 1, session.snapshot.value?.run?.day)

        // Trying again writes the very same day: nothing is played twice.
        val written = repo.run
        assertTrue(session.retry() is Result.Done)
        assertEquals(written, repo.run)
        assertEquals(2, session.snapshot.value?.run?.day)

        // A save that plainly failed is not "unconfirmed": the read said nothing was written.
        val next = session.snapshot.value!!.run!!
        session.run(Op.MoveCursor(DayCursor(next.lastResolution!!.commandId.value, DayCursor.Stage.DONE)))
        repo.failNextCommit = IOException("disk full")
        assertEquals(false, (session.run(Op.Dispatch(endDay(next), next.runId)) as Result.Failed).unconfirmed)
    }

    /** Review I5: a damaged database file is reported as such and left alone; only Start over moves it aside. */
    @Test
    fun aDamagedFileIsReportedLeftAloneAndSetAsideOnlyByStartOver() = sessionTest {
        val repo = liveRepo()
        repo.fileDamaged = true
        val session = session(repo)
        assertTrue(session.loadFailure() is SaveFailure.FileDamaged)
        assertTrue("trying again changes nothing", (session.retry() as Result.Failed).failure is SaveFailure.FileDamaged)
        assertEquals(0, repo.fileQuarantines)
        assertNull(session.snapshot.value)

        assertTrue(session.startOverKeepingBackup() is Result.Done)
        assertEquals(1, repo.fileQuarantines)
        assertTrue("no row is renamed inside a file that cannot be read", repo.quarantined.isEmpty())
        assertEquals(GameSession.Snapshot(null, LegacyProfile(), null), session.snapshot.value)
        assertTrue("and a new game can begin", session.run(Op.BeginEra(7L, null)) is Result.Done)
    }

    @Test
    fun retryGivesTheSameResolutionAsAFirstSuccess() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)

        val smooth = liveRepo(start)
        val first = session(smooth)
        first.load()
        val direct = first.run(Op.Dispatch(endDay(start), start.runId)) as Result.Done

        val rough = liveRepo(start)
        val second = session(rough)
        second.load()
        rough.failNextCommit = IOException("disk full")
        assertTrue(second.run(Op.Dispatch(endDay(start), start.runId)) is Result.Failed)
        val retried = second.retry() as Result.Done

        assertEquals(direct.accepted!!.resolution, retried.accepted!!.resolution)
        assertEquals(first.snapshot.value, second.snapshot.value)
        assertEquals("byte for byte the same day", smooth.run, rough.run)
        assertEquals(smooth.legacy, rough.legacy)
        assertEquals((engine.handle(start, endDay(start)) as CommandOutcome.Accepted).state, second.snapshot.value?.run)
    }

    @Test
    fun cancellationDuringCommitNeverPublishesAnUnsavedState() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()
        val before = session.snapshot.value
        repo.hold = true

        val job = launch { session.run(Op.Dispatch(endDay(start), start.runId)) }
        runCurrent()
        assertEquals("the commit is in flight", 1, repo.gates.size)
        job.cancel(); runCurrent()
        assertEquals("cancelled mid-commit: what is shown is still what is stored", before, session.snapshot.value)
        assertEquals(1, SaveCodec.decodeRun(repo.run!!).day)

        // The write was not abandoned half way: it completes, and only then is the new day shown.
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(2, SaveCodec.decodeRun(repo.run!!).day)
        assertEquals(SaveCodec.decodeRun(repo.run!!), session.snapshot.value?.run)
        assertEquals(Status.Idle, session.status.value)

    }

    @Test
    fun startOverQuarantinesInsteadOfDeleting() = sessionTest {
        val garbage = "not a save"
        val legacyRow = SaveCodec.encodeLegacy(veteran)
        val repo = repo(garbage, legacyRow)
        repo.cursor = "stale"
        val session = session(repo)
        assertTrue(session.loadFailure() is SaveFailure.Corrupt)
        assertTrue("retrying an unreadable save reads it again and fails the same way", (session.retry() as Result.Failed).failure is SaveFailure.Corrupt)
        assertEquals(garbage, repo.run)

        assertTrue(session.startOverKeepingBackup() is Result.Done)
        assertEquals("the unreadable row is kept under a backup key", garbage, repo.quarantined["run.bak"])
        assertNull(repo.run)
        assertEquals("the legacy is never touched", legacyRow, repo.legacy)
        assertEquals(GameSession.Snapshot(null, veteran, null), session.snapshot.value)
        assertTrue(session.run(Op.BeginEra(7L, null)) is Result.Done)
        assertEquals(12, session.snapshot.value?.run?.legacy?.points)

        // Once a save is loaded there is nothing to start over from: a good run is never set aside.
        val before = repo.run
        assertTrue(session.startOverKeepingBackup() is Result.Done)
        assertEquals(before, repo.run)

        // With no readable run beside it, an unreadable legacy row is set aside too (the only way forward), and kept just the same.
        val both = repo("also not a save", garbage)
        val lost = session(both)
        assertTrue(lost.loadFailure().let { it is SaveFailure.Corrupt && it.key == "legacy" })
        assertTrue(lost.startOverKeepingBackup() is Result.Done)
        assertEquals(garbage, both.quarantined["legacy.bak"])
        assertEquals("also not a save", both.quarantined["run.bak"])
        assertEquals(GameSession.Snapshot(null, LegacyProfile(), null), lost.snapshot.value)
    }

    /** Review I1: the run row carries the profile, so a damaged legacy row costs neither the run nor the legacy. */
    @Test
    fun anUnreadableLegacyRowNeverCostsAReadableRun() = sessionTest {
        val day2 = (engine.handle(engine.newRun(veteran, 42L), endDay(engine.newRun(veteran, 42L))) as CommandOutcome.Accepted).state
        val watched = DayCursor(day2.lastResolution!!.commandId.value, DayCursor.Stage.DONE).encode()
        val newer = SaveCodec.json.encodeToString(SaveEnvelope.serializer(), SaveEnvelope(SaveCodec.SCHEMA_VERSION + 1, "{}"))
        for (badLegacy in listOf("not a save", newer)) {
            val runRow = SaveCodec.encodeRun(day2)
            val repo = repo(runRow, badLegacy)
            repo.cursor = watched
            val session = session(repo)
            assertTrue(session.loadFailure().let { (it as? SaveFailure.Corrupt)?.key == "legacy" || (it as? SaveFailure.Newer)?.key == "legacy" })

            assertTrue(session.startOverKeepingBackup() is Result.Done)
            assertEquals("only the damaged row is set aside", mapOf("legacy.bak" to badLegacy), repo.quarantined)
            assertEquals("the run row is exactly as it was", runRow, repo.run)
            assertEquals("the legacy row is rebuilt from the copy the run carries", SaveCodec.encodeLegacy(day2.legacy), repo.legacy)
            assertEquals("the day already read stays read", watched, repo.cursor)
            val snap = session.snapshot.value!!
            assertEquals(day2, snap.run)
            assertEquals(12, snap.legacy.points)
            assertTrue("and the run plays on", session.run(Op.Dispatch(endDay(day2), day2.runId)).let { it is Result.Done && it.accepted?.resolution != null })
        }
    }

    /** The copy inside an ended run predates its claim, so after the restore the run can be claimed: once, for the same award. */
    @Test
    fun aClaimedRunRestoredFromItsOwnCopyIsClaimedExactlyOnceMore() = sessionTest {
        var ended = engine.newRun(veteran, 42L)
        while (!ended.isEnded) ended = (engine.handle(ended, endDay(ended)) as CommandOutcome.Accepted).state
        val award = engine.closeRun(ended).totalPoints
        val repo = repo(SaveCodec.encodeRun(ended), "not a save")
        repo.cursor = DayCursor(ended.lastResolution!!.commandId.value, DayCursor.Stage.DONE).encode()
        val session = session(repo)
        assertTrue(session.loadFailure() is SaveFailure.Corrupt)
        assertTrue(session.startOverKeepingBackup() is Result.Done)
        assertEquals(ended, session.snapshot.value?.run)
        assertEquals("the profile the run carried", ended.legacy, session.snapshot.value?.legacy)

        assertTrue(session.run(Op.Claim(ended.runId)) is Result.Done)
        assertEquals("exactly what the lost row held after its claim", veteran.points + award, session.snapshot.value?.legacy?.points)
        assertEquals(Result.Rejected(GameError.AlreadyClaimed(ended.runId)), session.run(Op.Claim(ended.runId)))
        assertEquals("the same profile an undamaged save holds after its claim", (engine.claimLegacy(ended.legacy, engine.closeRun(ended)) as LegacyOutcome.Updated).legacy, SaveCodec.decodeLegacy(repo.legacy!!))
    }

    /** Start over sets aside only what cannot be read: rows that are sound are left alone and simply loaded. */
    @Test
    fun startOverNeverSetsASoundRunAside() = sessionTest {
        val start = engine.newRun(veteran, 42L)
        val repo = repo(SaveCodec.encodeRun(start), SaveCodec.encodeLegacy(veteran))
        val session = session(repo)
        assertTrue(session.startOverKeepingBackup() is Result.Done)
        assertTrue(repo.quarantined.isEmpty())
        assertEquals(start, session.snapshot.value?.run)
    }
}
