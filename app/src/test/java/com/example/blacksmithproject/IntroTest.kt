package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.example.blacksmithproject.data.IntroRecord
import com.example.blacksmithproject.data.IntroStatus
import com.example.blacksmithproject.data.Settings
import com.example.blacksmithproject.ui.IntroMark
import com.example.blacksmithproject.ui.IntroStep
import com.example.blacksmithproject.ui.Tips
import com.example.blacksmithproject.ui.dayOutcome
import com.example.blacksmithproject.ui.introFor
import com.example.blacksmithproject.ui.introUi
import com.example.blacksmithproject.ui.pendingRelicOffer
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Journal
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guided first day over the fake store: what a new installation is shown, what ends it, and what it must never do
 * (repeat a step, come back in a later run, restart by itself, or touch a run).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntroTest : ShopDayTestBase() {
    private val sword = ForgeDraft(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)
    private fun newInstall() = FakeSettings(introRecord = IntroRecord())
    private fun GameViewModel.step() = playing().intro?.step

    /** A new installation's first game, taken as far as the first forge's result. */
    private suspend fun TestScope.firstForge(settings: FakeSettings, repo: FakeGameRepository = repo(null), saved: SavedStateHandle = SavedStateHandle()): GameViewModel {
        val vm = open(repo, settings, saved)
        vm.newRun(); advanceUntilIdle()
        vm.selectDest(Dest.FORGE); vm.updateDraft { sword }; advanceUntilIdle()
        vm.dispatch(sword.command()!!); advanceUntilIdle()
        return vm
    }

    @Test
    fun aNewInstallationIsGuidedFromItsFirstRecipeToTheFirstDaysOutcome() = vmTest {
        val settings = newInstall()
        val vm = open(repo(null), settings)
        assertEquals("queued before any game exists", IntroStatus.QUEUED, settings.introRecord.status)
        assertNull("a new account starts on the shop alone", vm.charter.value)

        vm.newRun(); advanceUntilIdle()
        assertNull(vm.playing().state.guild)
        assertEquals(IntroStep.RECIPE, vm.step())
        assertEquals("on the Shop the step points at the Forge", IntroMark.NAV_FORGE, vm.playing().intro!!.mark)
        assertTrue("the relic is on offer", vm.playing().state.pendingRelicOffer.isNotEmpty())
        assertFalse("and does not open by itself over the first step", vm.playing().pendingRelicOffer())
        vm.reopenRelicOffer(); advanceUntilIdle()
        assertTrue("asked for, it opens", vm.playing().pendingRelicOffer())
        vm.dismissRelicOffer()

        vm.selectDest(Dest.FORGE); advanceUntilIdle()
        assertNull(vm.playing().intro!!.mark)
        vm.updateDraft { sword }; advanceUntilIdle()
        assertEquals(IntroStep.FORGE, vm.step())
        assertEquals(IntroMark.FORGE, vm.playing().intro!!.mark)
        val config = engine.config
        assertTrue("the cost is the game's own number", "${config.quickForgeEnergy} of your ${config.baseDailyEnergy} energy" in vm.playing().intro!!.line.sentence)

        vm.dispatch(sword.command()!!); advanceUntilIdle()
        assertEquals(IntroStep.LIST, vm.step())
        assertNotNull("the result card carries the step", vm.playing().intro!!.result)
        val forged = vm.playing().revealWeaponId!!
        vm.listForged(forged, 40); advanceUntilIdle()
        assertEquals(IntroStep.SECOND, vm.step())
        assertNull("the second forge is not pointed at", vm.playing().intro!!.mark)

        vm.dispatch(sword.copy(coreId = LaunchContent.BRONZE).command()!!); advanceUntilIdle()
        vm.storeForged(); vm.selectDest(Dest.SHOP); advanceUntilIdle()
        assertEquals(IntroStep.END_DAY, vm.step())
        assertEquals(IntroMark.END_DAY, vm.playing().intro!!.mark)

        vm.endDay(); advanceUntilIdle()
        vm.skipDay(); advanceUntilIdle(); vm.acknowledge(); advanceUntilIdle()
        val outcome = vm.playing().intro!!
        assertEquals(IntroStep.OUTCOME, outcome.step)
        assertTrue(outcome.line.objective, outcome.line.objective.startsWith("Day 1: "))
        assertEquals("the record is not final until Done or the next End Day", IntroStatus.ACTIVE, settings.introRecord.status)

        vm.finishIntro(); advanceUntilIdle()
        assertNull(vm.playing().intro)
        assertEquals(IntroRecord(IntroStatus.COMPLETED, completedOnce = true), settings.introRecord)
        assertTrue("its two tips are not shown afterwards", settings.seenTips.value.containsAll(listOf(Tips.MARKET.id, Tips.END_DAY.id)))
    }

    @Test
    fun aStepOnceReachedDoesNotComeBack() = vmTest {
        val vm = firstForge(newInstall())
        val forged = vm.playing().revealWeaponId!!
        vm.listForged(forged, 40); advanceUntilIdle()
        assertEquals(IntroStep.SECOND, vm.step())
        // Off the shelf again, the recipe emptied, back on the Shop: none of it is asked for twice.
        vm.dispatch(Command.ToggleShelf(forged, false)); advanceUntilIdle()
        vm.updateDraft { ForgeDraft() }; vm.selectDest(Dest.SHOP); advanceUntilIdle()
        assertEquals(IntroStep.SECOND, vm.step())
        vm.dispatch(Command.Salvage(forged)); advanceUntilIdle()
        assertEquals(IntroStep.SECOND, vm.step())
    }

    @Test
    fun beforeTheFirstForgeTheStepFollowsTheWorkbench() = vmTest {
        val settings = newInstall()
        val store = repo(null)
        val vm = open(store, settings)
        vm.newRun(); advanceUntilIdle()
        vm.selectDest(Dest.FORGE); vm.updateDraft { sword }; advanceUntilIdle()
        assertEquals(IntroStep.FORGE, vm.step())
        vm.updateDraft { it.copy(coreId = null) }; advanceUntilIdle()
        assertEquals("a slot emptied is asked for again", IntroStep.RECIPE, vm.step())
        vm.updateDraft { sword }; advanceUntilIdle()
        // A cold start keeps the run and loses the recipe: "Forge it" is not said over an empty workbench.
        assertEquals(IntroStep.RECIPE, open(store, settings).step())
    }

    @Test
    fun skippingAtTheStartOrMidwayEndsItForThisRunAndEveryLaterOne() = vmTest {
        for (midway in listOf(false, true)) {
            val settings = newInstall()
            val vm = if (midway) firstForge(settings) else open(repo(null), settings).also { it.newRun(); advanceUntilIdle() }
            assertNotNull(vm.playing().intro)
            vm.skipIntro(); advanceUntilIdle()
            assertNull(vm.playing().intro)
            assertEquals(IntroStatus.SKIPPED, settings.introRecord.status)
            assertTrue("the relic offer is no longer kept back", midway || vm.playing().pendingRelicOffer())
            vm.abandonRun(); advanceUntilIdle()
            vm.newRun(); advanceUntilIdle()
            assertNull("midway=$midway: the next game has none", vm.playing().intro)
            assertEquals(IntroStatus.SKIPPED, settings.introRecord.status)
        }
    }

    @Test
    fun skippedBeforeTheFirstGameItNeverStarts() = vmTest {
        val settings = newInstall()
        val vm = open(repo(null), settings)
        vm.skipIntro(); advanceUntilIdle()
        vm.newRun(); advanceUntilIdle()
        assertNull(vm.playing().intro)
    }

    @Test
    fun aKilledProcessComesBackToTheSameStep() = vmTest {
        val settings = newInstall()
        val store = repo(null)
        val first = firstForge(settings, store)
        first.listForged(first.playing().revealWeaponId!!, 40); advanceUntilIdle()
        assertEquals(IntroStep.SECOND, first.step())
        // A cold start: the store and the settings, and nothing that was in memory.
        val again = open(store, settings)
        assertEquals(IntroStep.SECOND, again.step())
    }

    @Test
    fun anAbandonedRunStopsItAndTheNextGameIsNotGuidedUnlessAsked() = vmTest {
        val settings = newInstall()
        val vm = firstForge(settings)
        vm.abandonRun(); advanceUntilIdle()
        assertEquals("asked about, not restarted", IntroStatus.OFFERED, settings.introRecord.status)
        vm.newRun(); advanceUntilIdle()
        assertNull(vm.playing().intro)
        assertEquals(IntroStatus.OFFERED, settings.introRecord.status)
        vm.abandonRun(); advanceUntilIdle()
        // "Resume" on the title: with the next game, from its first step.
        vm.introAction(); advanceUntilIdle()
        assertEquals(IntroStatus.QUEUED, settings.introRecord.status)
        vm.newRun(); advanceUntilIdle()
        assertEquals(IntroStep.RECIPE, vm.step())
    }

    @Test
    fun itEndsByItselfWithTheSecondDay() = vmTest {
        val settings = newInstall()
        val vm = firstForge(settings)
        vm.storeForged()
        repeat(2) { vm.endDay(); advanceUntilIdle(); vm.skipDay(); advanceUntilIdle(); vm.acknowledge(); advanceUntilIdle() }
        assertEquals(3, vm.playing().state.day)
        assertNull(vm.playing().intro)
        assertEquals(IntroStatus.COMPLETED, settings.introRecord.status)
    }

    @Test
    fun whoHasPlayedBeforeIsNotTakenForABeginner() {
        val forgedOnce = LegacyProfile(journal = Journal(interactions = mapOf("ca:iron|ember_resin" to KnowledgeState.OBSERVED)))
        assertEquals(IntroStatus.SKIPPED, introFor(forgedOnce, null, emptySet()).status)
        assertEquals(IntroStatus.SKIPPED, introFor(LegacyProfile(points = 3, totalPointsEarned = 3), null, emptySet()).status)
        assertEquals("a run past its first day", IntroStatus.SKIPPED, introFor(LegacyProfile(), fresh.nextDay(), emptySet()).status)
        assertEquals("only a tip put away: asked, not assumed", IntroStatus.OFFERED, introFor(LegacyProfile(), fresh, setOf(Tips.MARKET.id)).status)
        assertEquals(IntroStatus.QUEUED, introFor(LegacyProfile(), null, emptySet()).status)
    }

    @Test
    fun anUpdateOverASaveInItsSecondDayShowsNothingAndRemembersIt() = vmTest {
        val settings = newInstall()
        val vm = open(planning(fresh.nextDay()), settings)
        assertNull(vm.playing().intro)
        assertEquals(IntroStatus.SKIPPED, settings.introRecord.status)
    }

    @Test
    fun aReplayWaitsForTheNextGameAndCanBeCancelledAndNeverTouchesTheRun() = vmTest {
        val settings = FakeSettings(introRecord = IntroRecord(IntroStatus.COMPLETED, completedOnce = true))
        val store = planning(fresh.nextDay())
        val vm = open(store, settings)
        val run = store.run
        assertEquals("Replay in next new game", vm.introRow.value!!.action)
        vm.introAction(); advanceUntilIdle()
        assertEquals(IntroStatus.QUEUED, settings.introRecord.status)
        assertEquals("Starts with your next new game." to "Cancel", vm.introRow.value!!.let { it.detail to it.action })
        assertNull(vm.playing().intro)
        assertEquals("the run is as it was", run, store.run)
        vm.introAction(); advanceUntilIdle()
        assertEquals("cancelled: back to completed", IntroRecord(IntroStatus.COMPLETED, completedOnce = true), settings.introRecord)
    }

    @Test
    fun aReplayInARunsFirstMorningStartsAtOnceAndCountsOnlyWhatIsForgedFromThen() = vmTest {
        val settings = FakeSettings(introRecord = IntroRecord(IntroStatus.SKIPPED))
        val forgedAlready = (engine.handle(fresh, forgeSword) as CommandOutcome.Accepted).state
        val store = repo(forgedAlready)
        val vm = open(store, settings)
        val run = store.run
        assertEquals("Replay now", vm.introRow.value!!.action)
        vm.introAction(); advanceUntilIdle()
        assertEquals("the weapon made before the replay is not its first forge", IntroStep.RECIPE, vm.step())
        assertEquals(run, store.run)
        assertEquals("In progress in this run." to "Skip guidance", vm.introRow.value!!.let { it.detail to it.action })
    }

    @Test
    fun aGuildRunIsGuidedOnlyOnceItsOpeningIsChosenAndAnOpeningsWeaponIsNotAForge() = vmTest {
        val settings = newInstall()
        val vm = open(repo(null), settings)
        vm.selectCharter(GuildContent.TOWNS_LAST_HOPE); advanceUntilIdle()
        assertEquals("the pick is kept", GuildContent.TOWNS_LAST_HOPE, settings.charter)
        vm.newRun(); advanceUntilIdle()
        assertNull("nothing over the opening", vm.playing().intro)
        assertEquals(IntroStatus.QUEUED, settings.introRecord.status)
        // Every package on offer, including those that bring a weapon, begins at the first step.
        for (offer in vm.playing().state.guild!!.openingOffer) {
            val chosen = (engine.handle(vm.playing().state, Command.ChooseOpening(offer.defId)) as CommandOutcome.Accepted).state
            val record = IntroRecord(IntroStatus.ACTIVE, chosen.runId.value, 0, chosen.nextWeaponSerial)
            assertEquals(offer.defId, IntroStep.RECIPE, engine.introUi(record, chosen, Dest.SHOP, ForgeDraft())!!.step)
        }
        vm.dispatch(Command.ChooseOpening(vm.playing().state.guild!!.openingOffer.first().defId)); advanceUntilIdle()
        assertEquals(IntroStep.RECIPE, vm.step())
    }

    @Test
    fun thePickersStartFollowsThePlayersLastPickAndOtherwiseTheAccount() = vmTest {
        val guild = engine.content.guild!!.charters.first().id
        assertNull("a new account", open(repo(null), FakeSettings()).charter.value)
        assertEquals("a new account that picked the guild", guild, open(repo(null), FakeSettings(charter = guild)).charter.value)
        val veteran = LegacyProfile(claimedRunIds = setOf("r1"))
        assertEquals("an account with an era behind it, as before", guild, open(repo(null, veteran), FakeSettings()).charter.value)
        assertNull("and its pick of the shop alone", open(repo(null, veteran), FakeSettings(charter = Settings.NO_CHARTER)).charter.value)
    }

    @Test
    fun aDayWithoutASaleSaysSoFromItsRecordAlone() {
        assertEquals("Day 1: nothing was on the shelf, so nothing could sell.", dayOutcome(fresh.nextDay().lastResolution!!))
    }

    @Test
    fun aForgeThatCannotBeRepeatedIsPassedOver() {
        val once = (engine.handle(fresh, forgeSword) as CommandOutcome.Accepted).state
        val listed = (engine.handle(once, Command.ToggleShelf(once.weapons.keys.first(), true, 40)) as CommandOutcome.Accepted).state
        val record = IntroRecord(IntroStatus.ACTIVE, fresh.runId.value, 0, fresh.nextWeaponSerial)
        assertEquals(IntroStep.SECOND, engine.introUi(record, listed, Dest.SHOP, ForgeDraft())!!.step)
        val spent = listed.copy(energy = 0, overworkToday = engine.config.maxOverworkPerDay)
        assertEquals(IntroStep.END_DAY, engine.introUi(record, spent, Dest.SHOP, ForgeDraft())!!.step)
    }
}
