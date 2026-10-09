package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.example.blacksmithproject.GameSession.Status
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.data.Settings
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.IOException
import org.junit.Test

class FakeSettings(var dismissed: String? = null) : Settings {
    override val reducedMotion = MutableStateFlow(false)
    override val haptics = MutableStateFlow(true)
    override val seenTips = MutableStateFlow(emptySet<String>())
    var dismissedReads = 0
    override suspend fun setReducedMotion(value: Boolean) { reducedMotion.value = value }
    override suspend fun setHaptics(value: Boolean) { haptics.value = value }
    override suspend fun markTipSeen(id: String) { seenTips.value += id }
    override suspend fun dismissedReport(): String? { dismissedReads++; return dismissed }
}

/** The ViewModel on a fake store: what the screen shows is derived from the session, and survives a new process. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val engine = GameEngine()
    private val repos = mutableListOf<FakeGameRepository>()

    private fun vmTest(body: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { body() } finally { repos.forEach { it.releaseAll() }; Dispatchers.resetMain() }
    }

    private fun repo(run: GameState?, legacy: LegacyProfile = run?.legacy ?: LegacyProfile()) =
        FakeGameRepository(run?.let { SaveCodec.encodeRun(it) }, SaveCodec.encodeLegacy(legacy)).also { repos += it }

    /** A new process over the same store: a fresh session and ViewModel. */
    private suspend fun TestScope.open(repo: FakeGameRepository, settings: Settings = FakeSettings(), saved: SavedStateHandle = SavedStateHandle()): GameViewModel =
        GameViewModel(engine, GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler)), settings, saved).also { advanceUntilIdle() }

    private fun endDay(s: GameState) = Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))
    private fun afterOneDay(): GameState = engine.newRun(LegacyProfile(), 42L).let { (engine.handle(it, endDay(it)) as CommandOutcome.Accepted).state }
    private val ended: GameState by lazy {
        var s = engine.newRun(LegacyProfile(), 42L)
        while (!s.isEnded) s = (engine.handle(s, endDay(s)) as CommandOutcome.Accepted).state
        s
    }

    private fun GameViewModel.playing() = ui.value as UiState.Playing
    private fun FakeGameRepository.storedCursor() = cursor?.let { DayCursor.decode(it) }

    @Test
    fun backNeverAcknowledgesAReport() = vmTest {
        val repo = repo(engine.newRun(LegacyProfile(), 42L))
        val vm = open(repo)
        assertNull("day 1 has no report", vm.playing().showReport)
        vm.endDay(); advanceUntilIdle()
        val report = vm.playing().showReport
        assertNotNull(report)

        // Back, or a tap outside the dialog, is swallowed: the report stays and the day stays unwatched.
        repeat(3) { assertTrue(vm.back()); advanceUntilIdle() }
        assertEquals(report, vm.playing().showReport)
        assertNull("nothing was acknowledged", repo.storedCursor())
        // While it is open planning is locked, and says nothing about it: the report is the explanation.
        val gold = vm.playing().state.gold
        vm.dispatch(Command.BuyMaterial(LaunchContent.IRON)); advanceUntilIdle()
        assertEquals(gold, vm.playing().state.gold)
        assertNull(vm.playing().lastError)
        // A new process shows the same report again.
        assertEquals(report, open(repo).playing().showReport)

        // Only the report's own button closes it, and that unlocks planning.
        vm.dismissReport(); advanceUntilIdle()
        assertNull(vm.playing().showReport)
        assertEquals(DayCursor(report!!.commandId.value, DayCursor.Stage.DONE), repo.storedCursor())
        vm.dispatch(Command.BuyMaterial(LaunchContent.IRON)); advanceUntilIdle()
        assertTrue(vm.playing().state.gold < gold)
        assertNull("closed stays closed in a new process", open(repo).playing().showReport)

        // With no report open, back goes to Shop from another destination and is not consumed on Shop (any page of it).
        vm.selectDest(Dest.RECORDS); advanceUntilIdle()
        assertEquals(Panel.GAZETTE, vm.playing().panel)
        assertTrue(vm.back()); advanceUntilIdle()
        assertEquals(Dest.SHOP to Panel.HOME, vm.playing().dest to vm.playing().panel)
        assertFalse(vm.back())
        vm.selectPanel(Panel.MARKET); advanceUntilIdle()
        assertEquals(Dest.SHOP, vm.playing().dest)
        assertFalse(vm.back())
        // A bar tap inside the destination already shown keeps its page.
        vm.selectPanel(Panel.LEGACY); vm.selectDest(Dest.RECORDS); advanceUntilIdle()
        assertEquals(Panel.LEGACY, vm.playing().panel)
    }

    @Test
    fun aReportClosedBeforeTheUpdateStaysClosedAndAnUnreadOneReopens() = vmTest {
        val day2 = afterOneDay()
        val id = day2.lastResolution!!.commandId.value

        // 0.6.0 left no cursor row; it kept the closed report's ID in settings.
        val closed = repo(day2)
        val settings = FakeSettings(dismissed = id)
        assertNull(open(closed, settings).playing().showReport)
        assertEquals("handed over to the cursor row", DayCursor(id, DayCursor.Stage.DONE), closed.storedCursor())
        // Honoured once: with the cursor row in place the old key is never read again.
        val reads = settings.dismissedReads
        assertNull(open(closed, settings).playing().showReport)
        assertEquals(reads, settings.dismissedReads)

        // A report that was never closed (the key names an earlier day, or nothing) reopens, as it did in 0.6.0.
        for (old in listOf(null, "some-earlier-day")) {
            val unread = repo(day2)
            assertEquals(day2.lastResolution, open(unread, FakeSettings(dismissed = old)).playing().showReport)
            assertNull(unread.storedCursor())
        }

        // An ended run whose report was closed in 0.6.0 opens on the run-end screen, unclaimed.
        val fallen = repo(ended)
        val end = open(fallen, FakeSettings(dismissed = ended.lastResolution!!.commandId.value)).ui.value
        assertTrue("got $end", end is UiState.RunEnded && !end.claimed)
    }

    @Test
    fun anUnreadableSaveOpensTheRecoveryScreenAndRetryLoadsItOnceRestored() = vmTest {
        val good = SaveCodec.encodeRun(afterOneDay())
        val repo = repo(null, LegacyProfile(points = 12))
        repo.run = "this is not a save"
        val vm = open(repo)
        val failed = vm.ui.value
        assertTrue("got $failed", failed is UiState.LoadFailed && failed.failure.let { it is SaveFailure.Corrupt && it.key == "run" } && !failed.working)
        vm.retry(); advanceUntilIdle()
        assertTrue("still unreadable", vm.ui.value is UiState.LoadFailed)
        assertEquals("this is not a save", repo.run)

        repo.run = good
        vm.retry(); advanceUntilIdle()
        assertEquals(2, vm.playing().state.day)

        // The other way out: start over. The row is kept as a backup and the legacy is still there.
        repo.run = "this is not a save"
        val again = open(repo)
        again.startOver(); advanceUntilIdle()
        val title = again.ui.value
        assertTrue("got $title", title is UiState.Title && title.legacy.points == 12)
        assertEquals("this is not a save", repo.quarantined["run.bak"])
    }

    @Test
    fun runEndReopensAfterAClaimWithUpgradesAvailable() = vmTest {
        val repo = repo(ended)
        val vm = open(repo)
        assertTrue("the last day's report comes first", vm.playing().showReport!!.defeated)
        vm.dismissReport(); advanceUntilIdle()
        assertFalse((vm.ui.value as UiState.RunEnded).claimed)
        vm.claimLegacy(); advanceUntilIdle()
        val claimed = vm.ui.value as UiState.RunEnded
        assertTrue(claimed.claimed && claimed.legacy.points > 0)

        // The process dies here. 0.6.0 deleted the run on claim and reopened on the title.
        val reopened = open(repo)
        val end = reopened.ui.value
        assertTrue("got $end", end is UiState.RunEnded && end.claimed && end.run.runId == ended.runId)
        assertEquals(claimed.legacy, (end as UiState.RunEnded).legacy)
        assertEquals(claimed.runEnd, end.runEnd)

        val upgrade = engine.content.upgrades.first { it.costPerLevel.first() <= end.legacy.points }
        reopened.buyUpgrade(upgrade.id); advanceUntilIdle()
        val bought = reopened.ui.value as UiState.RunEnded
        assertEquals(1, bought.legacy.upgradeLevel(upgrade.id))
        assertEquals(end.legacy.points - upgrade.costPerLevel.first(), bought.legacy.points)
        assertTrue("the ended run is still stored", SaveCodec.decodeRun(repo.run!!).isEnded)

        reopened.beginNextEra(); advanceUntilIdle()
        val next = reopened.playing()
        assertEquals(1, next.state.day)
        assertEquals(1, next.state.legacy.upgradeLevel(upgrade.id))
        assertNull(next.showReport)
    }

    @Test
    fun aFailedSaveKeepsTheLastSavedDayOnScreenAndCanBeRetriedOrDropped() = vmTest {
        val repo = repo(engine.newRun(LegacyProfile(), 42L))
        val vm = open(repo)
        repo.failNextCommit = IOException("disk full")
        vm.endDay(); advanceUntilIdle()
        assertTrue(vm.playing().op is Status.Failed)
        assertEquals(1, vm.playing().state.day)
        assertNull(vm.playing().showReport)

        vm.dismissSaveFailure(); advanceUntilIdle()
        assertEquals(Status.Idle, vm.playing().op)
        assertEquals(1, vm.playing().state.day)

        repo.failNextCommit = IOException("disk full")
        vm.endDay(); advanceUntilIdle()
        vm.retry(); advanceUntilIdle()
        assertEquals(Status.Idle, vm.playing().op)
        assertEquals(2, vm.playing().state.day)
        assertNotNull(vm.playing().showReport)
        assertEquals(1, repo.commitCount)
    }

    @Test
    fun aSecondTapWhileADayIsBeingSavedDoesNothing() = vmTest {
        val repo = repo(engine.newRun(LegacyProfile(), 42L))
        val vm = open(repo)
        repo.hold = true
        vm.endDay(); advanceUntilIdle()
        assertTrue("busy is shown while the save is in flight", vm.playing().busy)
        assertEquals("what is shown is still what is saved", 1, vm.playing().state.day)
        vm.endDay(); vm.dispatch(Command.BuyMaterial(LaunchContent.IRON)); advanceUntilIdle()
        repo.releaseAll(); advanceUntilIdle()
        assertEquals(2, vm.playing().state.day)
        assertEquals(1, repo.commitCount)
        assertFalse(vm.playing().busy)
    }
}
