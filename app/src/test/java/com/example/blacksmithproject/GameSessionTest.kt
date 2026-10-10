package com.example.blacksmithproject

import com.example.blacksmithproject.GameSession.Op
import com.example.blacksmithproject.GameSession.Result
import com.example.blacksmithproject.GameSession.Status
import com.example.blacksmithproject.data.SaveFailure
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The fake holds every commit at a gate, so "issued together" is exact: both ops are started, the scheduler runs
 * until nothing can move, and the test counts how many commits reached the store. No sleeps, no real threads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameSessionTest {
    private companion object {
        val engine = GameEngine()

        /** A smith who never forges falls at the second siege: a real ended run without test doubles. */
        val ended: GameState by lazy {
            var s = engine.newRun(LegacyProfile(), 42L)
            while (!s.isEnded) s = (engine.handle(s, endDay(s)) as CommandOutcome.Accepted).state
            s
        }

        fun endDay(s: GameState) = Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))
    }

    private val repos = mutableListOf<FakeGameRepository>()

    /**
     * The session commits under NonCancellable, so a commit left at its gate by a failed assertion could never be
     * cancelled and the test would hang instead of failing. Every repository is released on the way out.
     */
    private fun sessionTest(body: suspend TestScope.() -> Unit) = runTest {
        try { body() } finally { repos.forEach { it.releaseAll() } }
    }

    private fun TestScope.session(repo: FakeGameRepository) = GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler))

    private fun repo(run: GameState?, legacy: LegacyProfile?) =
        FakeGameRepository(run?.let { SaveCodec.encodeRun(it) }, legacy?.let { SaveCodec.encodeLegacy(it) }).also { repos += it }

    private fun liveRepo(run: GameState = engine.newRun(LegacyProfile(), 42L)) = repo(run, run.legacy)

    /** The ended run as it is stored before the claim: the legacy row is the profile the run carried. */
    private fun unclaimedRepo() = repo(ended, ended.legacy)

    private fun claimedLegacy() = LegacyProfile(points = 40, claimedRunIds = setOf(ended.runId.value))

    private fun claimedRepo() = repo(ended, claimedLegacy())

    /** What "See the legacy" does: the last day has been watched to its end. */
    private fun watched(run: GameState) = Op.MoveCursor(DayCursor(run.lastResolution!!.commandId.value, DayCursor.Stage.DONE))

    private fun storedLegacy(repo: FakeGameRepository) = SaveCodec.decodeLegacy(repo.legacy!!)
    private fun storedRun(repo: FakeGameRepository) = SaveCodec.decodeRun(repo.run!!)

    private fun twoUpgrades(first: com.tinyblacksmith.core.model.UpgradeId, second: com.tinyblacksmith.core.model.UpgradeId) = sessionTest {
        val repo = claimedRepo()
        val session = session(repo)
        session.load()
        repo.hold = true            // from here every commit waits until the test releases it

        val a = async { session.run(Op.BuyUpgrade(first, ended.runId)) }
        val b = async { session.run(Op.BuyUpgrade(second, ended.runId)) }
        runCurrent()
        assertEquals("only one commit may be in flight", 1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertEquals("the second commit starts only after the first finished", 1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertTrue(a.await() is Result.Done && b.await() is Result.Done)

        val stored = storedLegacy(repo)
        assertEquals(1, stored.upgradeLevel(LaunchContent.UPG_WALLS))
        assertEquals(1, stored.upgradeLevel(LaunchContent.UPG_ENERGY))
        assertEquals("both purchases were paid for exactly once", 40 - 8 - 8, stored.points)
        assertEquals("the ended run is kept, never deleted by an upgrade", ended.runId, storedRun(repo).runId)
        assertEquals("what is shown is what is stored", stored, session.snapshot.value?.legacy)
    }

    @Test
    fun twoUpgradesIssuedTogetherBothSurviveAndOnlyOneCommitIsEverInFlight() = twoUpgrades(LaunchContent.UPG_WALLS, LaunchContent.UPG_ENERGY)

    @Test
    fun twoUpgradesIssuedTogetherBothSurviveInTheOtherArrivalOrder() = twoUpgrades(LaunchContent.UPG_ENERGY, LaunchContent.UPG_WALLS)

    @Test
    fun upgradeIssuedBeforeBeginEraNeverClearsTheNewRun() = sessionTest {
        val repo = claimedRepo()
        val session = session(repo)
        session.load()
        repo.hold = true

        val upgrade = async { session.run(Op.BuyUpgrade(LaunchContent.UPG_WALLS, ended.runId)) }
        val era = async { session.run(Op.BeginEra(7L, ended.runId)) }
        runCurrent()
        assertEquals("the new era may not be computed while the upgrade is still being saved", 1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertEquals(1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertTrue(upgrade.await() is Result.Done && era.await() is Result.Done)

        val run = storedRun(repo)
        assertNotEquals("the new run is the stored run", ended.runId, run.runId)
        assertFalse(run.isEnded)
        assertEquals("the new run starts with the upgrade", 1, run.legacy.upgradeLevel(LaunchContent.UPG_WALLS))
        assertEquals(1, storedLegacy(repo).upgradeLevel(LaunchContent.UPG_WALLS))
        assertEquals(40 - 8, storedLegacy(repo).points)
        assertEquals(run, session.snapshot.value?.run)
        assertEquals(storedLegacy(repo), session.snapshot.value?.legacy)
    }

    @Test
    fun upgradeIssuedAfterBeginEraIsStaleAndNeverClearsTheNewRun() = sessionTest {
        val repo = claimedRepo()
        val session = session(repo)
        session.load()
        repo.hold = true

        val era = async { session.run(Op.BeginEra(7L, ended.runId)) }
        val upgrade = async { session.run(Op.BuyUpgrade(LaunchContent.UPG_WALLS, ended.runId)) }
        runCurrent()
        assertEquals(1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertEquals("the late upgrade writes nothing", 0, repo.gates.size)
        assertTrue(era.await() is Result.Done)
        assertEquals(Result.Stale, upgrade.await())

        assertEquals(1, repo.commitCount)
        assertFalse("the new run was not replaced by the ended one", storedRun(repo).isEnded)
        assertEquals("nothing was charged", 40, storedLegacy(repo).points)
    }

    @Test
    fun endDayQueuedAgainstAnOldRunIsStale() = sessionTest {
        val repo = claimedRepo()
        val session = session(repo)
        session.load()
        repo.hold = true

        val era = async { session.run(Op.BeginEra(7L, ended.runId)) }
        val queued = async { session.run(Op.Dispatch(Command.EndDay(CommandId("${ended.runId.value}:day${ended.day}")), ended.runId)) }
        runCurrent()
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertTrue(era.await() is Result.Done)
        assertEquals(Result.Stale, queued.await())

        assertEquals(0, repo.gates.size)
        assertEquals(1, repo.commitCount)
        assertEquals("the new era is still on its first morning", 1, storedRun(repo).day)
        assertNull(storedRun(repo).lastResolution)
    }

    @Test
    fun abandoningDiscardsTheRunAndClaimsNothing() = sessionTest {
        val live = engine.newRun(LegacyProfile(points = 12), 43L)       // not seed 42: that is the ended run's ID
        val repo = liveRepo(live)
        val session = session(repo)
        session.load()
        assertEquals(Result.Stale, session.run(Op.Abandon(ended.runId)))
        assertTrue(session.run(Op.Abandon(live.runId)) is Result.Done)
        assertNull("the run row is gone", repo.run)
        assertNull(session.snapshot.value?.run)
        assertEquals("nothing is claimed", live.legacy, storedLegacy(repo))
        assertEquals("a second tap finds no run", Result.Stale, session.run(Op.Abandon(live.runId)))
        assertTrue("a new game starts from the menu", session.run(Op.BeginEra(7L, null)) is Result.Done)

        // A run that has ended is claimed on the run-end screen, never abandoned.
        val over = session(unclaimedRepo())
        over.load()
        assertEquals(Result.Rejected(GameError.RunEnded), over.run(Op.Abandon(ended.runId)))
    }

    @Test
    fun aRunWithoutALegacyRowReadsTheLegacyItCarries() = sessionTest {
        val live = engine.newRun(LegacyProfile(points = 12), 43L)
        val session = session(repo(live, null))
        session.load()
        assertEquals(live.legacy, session.snapshot.value?.legacy)
    }

    @Test
    fun doubleBeginEraCreatesOneRun() = sessionTest {
        // From the run-end screen.
        val repo = claimedRepo()
        val session = session(repo)
        session.load()
        repo.hold = true
        val a = async { session.run(Op.BeginEra(7L, ended.runId)) }
        val b = async { session.run(Op.BeginEra(8L, ended.runId)) }
        runCurrent()
        assertEquals(1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertEquals(0, repo.gates.size)
        assertTrue(a.await() is Result.Done)
        assertEquals(Result.Stale, b.await())
        assertEquals(1, repo.commitCount)
        assertEquals(7L, storedRun(repo).seed)
        assertEquals(7L, session.snapshot.value?.run?.seed)

        // From the title, with no run at all.
        val empty = repo(null, null)
        val fresh = session(empty)
        fresh.load()
        empty.hold = true
        val c = async { fresh.run(Op.BeginEra(7L, null)) }
        val d = async { fresh.run(Op.BeginEra(8L, null)) }
        runCurrent()
        empty.gates.removeFirst().complete(Unit); runCurrent()
        assertTrue(c.await() is Result.Done)
        assertEquals(Result.Stale, d.await())
        assertEquals(1, empty.commitCount)
        assertEquals(7L, storedRun(empty).seed)
    }

    @Test
    fun doubleEndDayCommitsOnce() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()
        repo.hold = true

        val a = async { session.run(Op.Dispatch(endDay(start), start.runId)) }
        val b = async { session.run(Op.Dispatch(endDay(start), start.runId)) }
        runCurrent()
        assertEquals(1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertEquals("the second End Day never reaches the store", 0, repo.gates.size)
        val first = a.await()
        assertTrue(first is Result.Done && first.accepted?.resolution != null)
        assertEquals("the day it would replay has not been watched yet", Result.DayNotWatched, b.await())
        assertEquals(1, repo.commitCount)
        assertEquals(2, storedRun(repo).day)

        // Once the day has been watched, the same command ID is the engine's idempotent replay: Done, nothing written.
        session.run(watched(session.snapshot.value!!.run!!))
        val replay = session.run(Op.Dispatch(endDay(start), start.runId))
        assertTrue(replay is Result.Done)
        assertEquals(1, repo.commitCount)
        assertEquals(2, session.snapshot.value?.run?.day)
    }

    @Test
    fun doubleClaimAwardsOnce() = sessionTest {
        val repo = unclaimedRepo()
        val runRow = repo.run
        val session = session(repo)
        session.load()
        assertEquals("the last day must be watched before the claim", Result.Rejected(GameError.RunNotEnded), session.run(Op.Claim(ended.runId)))
        session.run(watched(ended))
        repo.hold = true

        val a = async { session.run(Op.Claim(ended.runId)) }
        val b = async { session.run(Op.Claim(ended.runId)) }
        runCurrent()
        assertEquals(1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertEquals(0, repo.gates.size)
        assertTrue(a.await() is Result.Done)
        assertEquals(Result.Rejected(GameError.AlreadyClaimed(ended.runId)), b.await())

        val award = engine.closeRun(ended).totalPoints
        assertTrue(award > 0)
        assertEquals(1, repo.commitCount)
        assertEquals(award, storedLegacy(repo).points)
        assertEquals(1, storedLegacy(repo).eras.size)
        assertEquals("the ended run stays stored, byte for byte", runRow, repo.run)
    }

    @Test
    fun runEndSurvivesRecreationAfterClaim() = sessionTest {
        val repo = unclaimedRepo()
        val first = session(repo)
        first.load()
        first.run(watched(ended))
        assertTrue(first.run(Op.Claim(ended.runId)) is Result.Done)

        // The process dies; a new session reads the same rows.
        val second = session(repo)
        assertTrue(second.load() is Result.Done)
        val snap = second.snapshot.value!!
        assertEquals("the ended run is still there to rebuild the run-end screen from", ended, snap.run)
        assertTrue("claimed is read from the legacy row", ended.runId.value in snap.legacy.claimedRunIds)
        assertEquals(engine.closeRun(ended).totalPoints, snap.legacy.points)
        assertEquals(DayCursor.Stage.DONE, GameSession.pending(snap.run, snap.legacy, snap.cursor))
        assertEquals(Result.Rejected(GameError.AlreadyClaimed(ended.runId)), second.run(Op.Claim(ended.runId)))
        assertTrue("the next era can still begin", second.run(Op.BeginEra(7L, ended.runId)) is Result.Done)
        assertEquals(2, storedRun(repo).era)
    }

    @Test
    fun nothingIsPublishedUntilTheSaveCompletes() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()
        val before = session.snapshot.value
        repo.hold = true

        val op = Op.Dispatch(endDay(start), start.runId)
        val a = async { session.run(op) }
        runCurrent()
        assertEquals("the commit is in flight", 1, repo.gates.size)
        assertEquals("the day is computed but not shown", before, session.snapshot.value)
        assertEquals(Status.Working(op), session.status.value)

        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertTrue(a.await() is Result.Done)
        assertEquals(2, session.snapshot.value?.run?.day)
        assertEquals("what is shown is what is stored", storedRun(repo), session.snapshot.value?.run)
        assertEquals(Status.Idle, session.status.value)
    }

    @Test
    fun anEndDayReplayAfterClaimNeverRewritesTheLegacyRow() = sessionTest {
        val repo = unclaimedRepo()
        val session = session(repo)
        session.load()
        session.run(watched(ended))
        session.run(Op.Claim(ended.runId))
        assertTrue(session.run(Op.BuyUpgrade(LaunchContent.UPG_WALLS, ended.runId)) is Result.Done)
        val legacyRow = repo.legacy
        val commits = repo.commitCount
        assertNotEquals("the run still embeds the profile from before the claim", storedLegacy(repo), storedRun(repo).legacy)

        val lastDay = Command.EndDay(ended.lastResolution!!.commandId)
        assertEquals(Result.Rejected(GameError.RunEnded), session.run(Op.Dispatch(lastDay, ended.runId)))
        assertEquals(commits, repo.commitCount)
        assertEquals(legacyRow, repo.legacy)
        assertEquals(1, session.snapshot.value?.legacy?.upgradeLevel(LaunchContent.UPG_WALLS))
    }

    @Test
    fun buyUpgradeDuringALiveRunIsRejected() = sessionTest {
        val start = engine.newRun(LegacyProfile(points = 40), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()
        assertEquals(Result.Rejected(GameError.RunNotEnded), session.run(Op.BuyUpgrade(LaunchContent.UPG_WALLS, start.runId)))
        assertEquals(0, repo.commitCount)

        // Ended but not claimed: still the run's profile, so still no purchase.
        val unclaimed = unclaimedRepo()
        val other = session(unclaimed)
        other.load()
        assertEquals(Result.Rejected(GameError.RunNotEnded), other.run(Op.BuyUpgrade(LaunchContent.UPG_WALLS, ended.runId)))
        assertEquals(0, unclaimed.commitCount)
    }

    @Test
    fun claimOnALiveRunIsRejected() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()
        assertEquals(Result.Rejected(GameError.RunNotEnded), session.run(Op.Claim(start.runId)))
        assertEquals(0, repo.commitCount)
        assertEquals(LegacyProfile(), storedLegacy(repo))
    }

    @Test
    fun beginEraBeforeClaimIsRejected() = sessionTest {
        val repo = unclaimedRepo()
        val runRow = repo.run
        val session = session(repo)
        session.load()
        session.run(watched(ended))
        assertEquals(Result.Rejected(GameError.RunNotEnded), session.run(Op.BeginEra(7L, ended.runId)))
        assertEquals(0, repo.commitCount)
        assertEquals("the unclaimed run is still stored", runRow, repo.run)

        val start = engine.newRun(LegacyProfile(), 42L)
        val live = liveRepo(start)
        val other = session(live)
        other.load()
        assertEquals(Result.Rejected(GameError.RunNotEnded), other.run(Op.BeginEra(7L, start.runId)))
        assertEquals(0, live.commitCount)
    }

    @Test
    fun anEngineFaultIsNotRetried() = sessionTest {
        // The save is sound (load admits it); the fault is in the engine: a config with no risk table throws inside the forge.
        val faulty = GameEngine(config = BalanceConfig.DEFAULT.copy(risk = emptyMap()))
        val start = faulty.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val runRow = repo.run
        val session = GameSession(faulty, repo, compute = StandardTestDispatcher(testScheduler))
        assertTrue(session.load() is Result.Done)
        val before = session.snapshot.value

        val result = session.run(Op.Dispatch(Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED), start.runId))
        assertTrue("got $result", result is Result.EngineFault)
        assertEquals("nothing is held for a retry", Status.Idle, session.status.value)
        assertTrue("a retry has nothing to run", session.retry().let { it is Result.Done && it.accepted == null })
        assertEquals(0, repo.commitCount)
        assertEquals(runRow, repo.run)
        assertEquals(before, session.snapshot.value)
    }

    @Test
    fun aSecondLoadCannotPublishOverANewerSnapshot() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()

        repo.holdLoad = true        // this load has read day 1 and is slow to return
        val load = async { session.load() }
        runCurrent()
        assertEquals(1, repo.loadGates.size)
        repo.holdLoad = false
        val day = async { session.run(Op.Dispatch(endDay(start), start.runId)) }
        runCurrent()
        assertEquals("End Day waits for the load", 0, repo.commitCount)

        repo.loadGates.removeFirst().complete(Unit); runCurrent()
        assertTrue(load.await() is Result.Done && day.await() is Result.Done)
        assertEquals(2, storedRun(repo).day)
        assertEquals("the stale read was not published over the committed day", storedRun(repo), session.snapshot.value?.run)
    }

    @Test
    fun aFailedCommitPublishesNothingAndIsHeldInStatus() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val runRow = repo.run
        val session = session(repo)
        session.load()
        val before = session.snapshot.value
        repo.failNextCommit = IOException("disk full")

        val op = Op.Dispatch(endDay(start), start.runId)
        val result = session.run(op)
        assertTrue(result is Result.Failed && result.failure is SaveFailure.Io)
        assertEquals(runRow, repo.run)
        assertEquals("nothing has changed", before, session.snapshot.value)
        val status = session.status.value
        assertTrue(status is Status.Failed && status.op == op)

        // Issued again from the same snapshot (RNG state is part of it), the day is the same day.
        assertTrue(session.run(op) is Result.Done)
        assertEquals((engine.handle(start, endDay(start)) as CommandOutcome.Accepted).state, session.snapshot.value?.run)
        assertEquals(Status.Idle, session.status.value)
    }

    @Test
    fun aCommitThatLandedBeforeItsFailureSurfacedIsPublished() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()
        val loads = repo.loadCount
        repo.failNextCommitAfterWriting = IOException("connection closed after commit")

        assertTrue(session.run(Op.Dispatch(endDay(start), start.runId)) is Result.Done)
        assertEquals("one confirmation read", loads + 1, repo.loadCount)
        assertEquals(2, session.snapshot.value?.run?.day)
        assertEquals(storedRun(repo), session.snapshot.value?.run)
        assertEquals(Status.Idle, session.status.value)
    }

    @Test
    fun planningIsLockedUntilTheDayIsWatched() = sessionTest {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = liveRepo(start)
        val session = session(repo)
        session.load()
        val buy = Op.Dispatch(Command.BuyMaterial(engine.content.materialById.keys.first()), start.runId)
        assertNotEquals("day 1 has nothing to watch", Result.DayNotWatched, session.run(buy))

        val resolved = session.run(Op.Dispatch(endDay(start), start.runId)) as Result.Done
        val id = resolved.accepted!!.resolution!!.commandId.value
        val commits = repo.commitCount
        assertEquals("the stored cursor is missing, so the day is unwatched", Result.DayNotWatched, session.run(buy))
        session.run(Op.MoveCursor(DayCursor(id, DayCursor.Stage.AFTERMATH)))
        assertEquals(Result.DayNotWatched, session.run(buy))
        session.run(Op.MoveCursor(DayCursor(id, DayCursor.Stage.TOMORROW)))
        assertEquals(Result.DayNotWatched, session.run(buy))
        assertNotEquals("a blessing may be chosen on the Tomorrow card", Result.DayNotWatched,
            session.run(Op.Dispatch(Command.ChooseBlessing(com.tinyblacksmith.core.model.BlessingId("none")), start.runId)))
        assertEquals(commits, repo.commitCount)

        // The move to DONE is not lost with a failing cursor row: the snapshot still moves.
        repo.failNextCursor = IOException("disk full")
        assertTrue(session.run(Op.MoveCursor(DayCursor(id, DayCursor.Stage.DONE))) is Result.Done)
        assertEquals(DayCursor.Stage.TOMORROW, DayCursor.decode(repo.cursor!!).stage)
        assertEquals(DayCursor.Stage.DONE, session.snapshot.value?.cursor?.stage)
        assertNotEquals(Result.DayNotWatched, session.run(buy))
        assertEquals(Status.Idle, session.status.value)
    }
}
