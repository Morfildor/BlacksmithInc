package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.example.blacksmithproject.GameSession.Op
import com.example.blacksmithproject.GameSession.Result
import com.example.blacksmithproject.ui.bellowsBlocked
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.endDayNote
import com.example.blacksmithproject.ui.pendingRelicOffer
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.visitorNote
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.model.ActiveRelic
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionKind
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.EncounterStatus
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Morning visitors, workshop relics and the bellows through the session and the ViewModel: planning only, saved before
 * shown, answered exactly once, and unchanged by anything that is not an answer (a replay, a closed sheet, a dead process).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameplayDepthTest : ShopDayTestBase() {
    private val content get() = engine.content
    private val config get() = engine.config

    /** Day 1 with the opening relic draft out of the way, so nothing but the visitor is waiting. */
    private val quiet: GameState get() = fresh.copy(pendingRelicOffer = emptyList())

    private fun GameState.withVisitor(defId: String = Depth.FESTIVAL_CONTRACT): GameState =
        Encounters.force(this, content, config, defId) ?: error("$defId is not eligible in this fixture")

    private fun stored(repo: FakeGameRepository) = SaveCodec.decodeRun(repo.run!!)

    @Test
    fun visitorAndRelicCommandsAreRefusedAtEveryStageOfTheShopDay() = vmTest {
        // A resolved day that has not been watched, with a visitor at the forge and the opening relic draft still open.
        val run = stocked.nextDay().withVisitor()
        assertTrue("the fixture has a relic offer", run.pendingRelicOffer.isNotEmpty())
        val repo = repo(run)
        val session = session(repo)
        session.load()
        val day = run.lastResolution!!.commandId.value
        val commands = listOf(
            Command.ResolveEncounter(run.encounter!!.id, Encounters.PASS, CommandId("gate")),
            Command.ChooseRelic(run.pendingRelicOffer.first()),
            Command.DeclineRelicOffer,
        )
        // No cursor row at all counts as the counter; TOMORROW lets a blessing through and nothing else.
        for (stage in listOf(null, DayCursor.Stage.COUNTER, DayCursor.Stage.AFTERMATH, DayCursor.Stage.TOMORROW)) {
            stage?.let { session.run(Op.MoveCursor(DayCursor(day, it))) }
            commands.forEach { assertEquals("$it at $stage", Result.DayNotWatched, session.run(Op.Dispatch(it, run.runId))) }
        }
        assertEquals(0, repo.commitCount)
        assertEquals(run.encounter, session.snapshot.value?.run?.encounter)

        session.run(Op.MoveCursor(DayCursor(day, DayCursor.Stage.DONE)))
        assertTrue(session.run(Op.Dispatch(commands[0], run.runId)) is Result.Done)
        assertTrue(session.run(Op.Dispatch(commands[1], run.runId)) is Result.Done)
        assertNotEquals("the offer was just taken: refused by the engine, not by the gate", Result.DayNotWatched, session.run(Op.Dispatch(commands[2], run.runId)))
        assertEquals(EncounterStatus.RESOLVED, stored(repo).encounter?.status)
        assertEquals(1, stored(repo).relics.size)
    }

    @Test
    fun watchingSkippingOrLeavingTheReplayNeverAnswersTheVisitor() = vmTest {
        val run = stocked.nextDay().withVisitor()
        val repo = repo(run)
        val vm = open(repo)
        assertTrue(vm.ui.value is UiState.ShopDay)
        vm.resumeDay()
        // An answer sent while the day is on screen goes nowhere, and neither Skip, Back nor the last card is an answer.
        vm.answerVisitor(Encounters.PASS); advanceUntilIdle()
        vm.skipDay(); advanceUntilIdle()
        vm.back(); advanceUntilIdle()
        vm.answerVisitor(Encounters.PASS); advanceUntilIdle()
        watchToTheEnd(vm)
        vm.acknowledge(); advanceUntilIdle()
        assertEquals("nothing was written", 0, repo.commitCount)
        val view = vm.playing().encounter!!
        assertEquals(run.encounter, view.instance)
        assertTrue(view.instance.isOpen)
        assertEquals("The visitor leaves tonight. No answer means ${view.defaultLabel.lowercase()}.", endDayNote(vm.playing().state, view))
    }

    @Test
    fun anAnswerIsSavedBeforeItShowsAndTheSameTapAgainChangesNothing() = vmTest {
        val repo = repo(quiet.withVisitor())
        val vm = open(repo)
        val view = vm.playing().encounter!!
        val option = view.options.first { it.id != Encounters.PASS && it.blocked == null }
        val morning = repo.run
        vm.openSheet(Sheet.Visitor)

        repo.hold = true
        vm.answerVisitor(option.id); advanceUntilIdle()
        assertTrue("the commit is in flight", vm.playing().busy)
        assertEquals("nothing is shown before it is saved", view, vm.playing().encounter)
        assertEquals(morning, repo.run)
        repo.releaseAll(); advanceUntilIdle()

        val answered = vm.playing()
        assertEquals(EncounterStatus.RESOLVED to option.id, answered.encounter!!.instance.let { it.status to it.chosen })
        assertEquals("what is shown is what is stored", stored(repo), answered.state)
        assertEquals(1, repo.commitCount)
        assertEquals("${view.name}: you answered \"${option.label}\".", visitorNote(answered.encounter))
        assertNotEquals("the visitor no longer holds up End Day", "The visitor leaves tonight. No answer means ${view.defaultLabel.lowercase()}.", endDayNote(answered.state, answered.encounter))

        // The same tap again is the same command: accepted, nothing written, nothing said.
        val after = repo.run
        vm.answerVisitor(option.id); advanceUntilIdle()
        assertEquals(1, repo.commitCount)
        assertEquals(after, repo.run)
        assertNull(vm.playing().lastError)
        // Any other answer comes too late.
        vm.answerVisitor(Encounters.PASS); advanceUntilIdle()
        assertEquals("You've already answered this visitor.", vm.playing().lastError)
        assertEquals(after, repo.run)
    }

    @Test
    fun anAnswerTheEngineRefusesShowsItsReason() = vmTest {
        val repo = repo(quiet.copy(gold = 0).withVisitor())
        val vm = open(repo)
        val view = vm.playing().encounter!!
        val closed = view.options.first { it.blocked != null }
        vm.answerVisitor(closed.id); advanceUntilIdle()
        assertEquals(closed.blocked, vm.playing().lastError)
        assertEquals(0, repo.commitCount)
        assertEquals("the visitor still waits, as they were", view, vm.playing().encounter)
    }

    @Test
    fun theVisitorIsTheSameAfterTheSheetIsClosedAndAfterTheProcessDies() = vmTest {
        val saved = SavedStateHandle()
        val repo = repo(quiet.withVisitor(Depth.CROOKED_MERCHANT))
        val first = open(repo, saved = saved)
        val view = first.playing().encounter!!
        val reference = repo.twin()

        // Later and reopen: the sheet is a view of the save and writes nothing.
        first.openSheet(Sheet.Visitor); first.closeSheet(); first.openSheet(Sheet.Visitor); advanceUntilIdle()
        assertEquals(view, first.playing().encounter)
        assertEquals(0, repo.commitCount)

        // The process dies with an answer computed and not yet stored: the visitor is there, unanswered, with the sheet open on it.
        repo.hold = true
        first.answerVisitor("inspect"); advanceUntilIdle()
        val left = repo.twin()
        val again = open(left, saved = saved)
        assertEquals(view, again.playing().encounter)
        assertEquals(Sheet.Visitor, again.playing().sheet)

        // The inspection leaves the merchant at the forge with other words and fewer answers; asked again it changes nothing.
        again.answerVisitor("inspect"); advanceUntilIdle()
        val inspected = again.playing().encounter!!
        assertTrue(inspected.instance.isOpen && inspected.instance.inspected)
        assertNotEquals(view.text, inspected.text)
        assertTrue(inspected.options.size < view.options.size)
        assertEquals(Sheet.Visitor, again.playing().sheet)
        again.answerVisitor("inspect"); advanceUntilIdle()
        assertEquals(1, left.commitCount)
        assertNull(again.playing().lastError)
        // Had the first process lived, it would have stored exactly this.
        repo.releaseAll(); advanceUntilIdle()
        assertEquals(left.run, repo.run)
        assertEquals(inspected, open(left.twin()).playing().encounter)

        // And an undisturbed process reaches the same save by the same answer.
        open(reference).also { it.answerVisitor("inspect"); advanceUntilIdle() }
        assertEquals(reference.run, left.run)
    }

    @Test
    fun endDayNoteNamesWhatIsLeftOpenMostPressingFirst() {
        val run = quiet.withVisitor()
        val view = engine.encounterView(run)!!
        assertEquals("The visitor leaves tonight. No answer means ${view.defaultLabel.lowercase()}.", endDayNote(run, view))
        assertEquals("Choose a workshop relic", endDayNote(fresh, engine.encounterView(fresh)))
        val blessed = run.copy(pendingBlessingOffer = listOf(content.blessings.first().id), pendingRelicOffer = fresh.pendingRelicOffer)
        assertEquals("Choose a blessing", endDayNote(blessed, view))
        assertEquals("the visitor before the relic", "The visitor leaves tonight. No answer means ${view.defaultLabel.lowercase()}.", endDayNote(blessed.copy(pendingBlessingOffer = emptyList()), view))
        assertEquals("${quiet.energy} energy unused", endDayNote(quiet, null))
    }

    @Test
    fun aRelicCanBePutOffChosenOrDeclined() = vmTest {
        val saved = SavedStateHandle()
        val repo = repo(fresh)
        val vm = open(repo, saved = saved)
        val offer = vm.playing().state.pendingRelicOffer
        assertTrue("a new run opens with a relic draft", offer.isNotEmpty())
        assertNull(vm.playing().relicOfferDismissedDay)

        // Decide later: UI-only, kept across a new process, and the offer stays in the save.
        vm.dismissRelicOffer(); advanceUntilIdle()
        assertEquals(fresh.day, vm.playing().relicOfferDismissedDay)
        assertEquals(0, repo.commitCount)
        assertEquals(fresh.day, open(repo, saved = saved).playing().relicOfferDismissedDay)
        vm.reopenRelicOffer(); advanceUntilIdle()
        assertNull(vm.playing().relicOfferDismissedDay)

        vm.chooseRelic(offer.first()); advanceUntilIdle()
        assertEquals(listOf(offer.first()), vm.playing().relics.map { it.id })
        assertTrue(vm.playing().state.pendingRelicOffer.isEmpty())
        assertEquals(stored(repo), vm.playing().state)

        // With a blessing also on offer, the relic is not held back once the blessing has been put off (the screen shows the blessing first).
        val both = open(repo(fresh.copy(pendingBlessingOffer = listOf(content.blessings.first().id))))
        both.dismissBlessingOffer(); both.dismissRelicOffer(); advanceUntilIdle()
        assertFalse(both.playing().pendingRelicOffer())
        both.reopenRelicOffer(); advanceUntilIdle()
        assertTrue(both.playing().pendingRelicOffer())

        // Take none: the offer is gone and nothing is held.
        val other = repo(fresh)
        val declining = open(other)
        declining.declineRelics(); advanceUntilIdle()
        assertTrue(stored(other).pendingRelicOffer.isEmpty())
        assertTrue(declining.playing().relics.isEmpty())
    }

    @Test
    fun withEverySlotTakenARelicMustReplaceOne() = vmTest {
        val all = content.relics.map { it.id }
        val held = all.take(config.depth.relicSlots)
        val offered = all[config.depth.relicSlots]
        val repo = repo(fresh.copy(relics = held.map { ActiveRelic(it) }, pendingRelicOffer = listOf(offered)))
        val vm = open(repo)

        vm.chooseRelic(offered); advanceUntilIdle()
        assertEquals("Your relic slots are full. Choose one to replace.", vm.playing().lastError)
        assertEquals(held, vm.playing().relics.map { it.id })
        assertEquals(0, repo.commitCount)

        vm.chooseRelic(offered, replaceId = held[1]); advanceUntilIdle()
        assertNull(vm.playing().lastError)
        assertEquals((held - held[1] + offered).toSet(), vm.playing().relics.map { it.id }.toSet())
        assertTrue(stored(repo).pendingRelicOffer.isEmpty())
    }

    @Test
    fun theBellowsSwitchReachesTheForgeCommandOnceAndIsSpentWithIt() = vmTest {
        val saved = SavedStateHandle()
        val repo = repo(fresh.copy(relics = listOf(ActiveRelic(Depth.ASHEN_BELLOWS)), pendingRelicOffer = emptyList()))
        val vm = open(repo, saved = saved)
        assertNull("held, unused and affordable", bellowsBlocked(vm.playing(), config))
        vm.updateDraft { ForgeDraft(familyId = LaunchContent.SWORD, coreId = LaunchContent.IRON, augmentId = LaunchContent.EMBER_RESIN, bellows = true) }
        advanceUntilIdle()
        assertTrue("the switch survives a new process", open(repo, saved = saved).playing().draft.bellows)

        val command = vm.playing().draft.command()!!
        assertTrue(command.bellows)
        assertEquals(forgeSword.copy(bellows = true), command)
        vm.dispatch(command); advanceUntilIdle()
        val forged = vm.playing()
        assertEquals("the bellows took their debt from tomorrow", config.depth.bellowsDebt, forged.state.overworkToday)
        assertEquals(false, forged.relics.single().ready)
        assertFalse("the next draft starts without them", forged.draft.bellows)
        assertEquals("Used today.", bellowsBlocked(forged, config))
        assertEquals(false, forged.draft.command()?.bellows)

        // Asked for again the same day, the engine refuses and the screen says why.
        vm.dispatch(command); advanceUntilIdle()
        assertEquals("Ashen Bellows has already been used today.", vm.playing().lastError)
        assertEquals(1, vm.playing().state.weapons.size)
    }

    @Test
    fun aBladeKeptForAnOrderSaysSoAndSalvageNamesTheAugmentTheCrucibleReturns() = vmTest {
        val made = engine.handle(quiet, forgeSword) as CommandOutcome.Accepted
        val blade = made.state.weapons.getValue(made.forgedWeaponId!!)
        // Fine enough for the crucible, which the workshop holds unused.
        val fine = blade.copy(quality = config.rareMin)
        val run = made.state.copy(weapons = mapOf(fine.id to fine), relics = listOf(ActiveRelic(Depth.SALVAGERS_CRUCIBLE)))
        val salvage = engine.itemDetail(run, fine.id)!!.stock!!.salvage
        assertTrue(salvage, salvage.endsWith("also returns ${content.material(fine.augmentId).name})"))
        assertNull(engine.shopUi(run).storage.single().promised)
        assertFalse(engine.itemDetail(made.state, blade.id)!!.stock!!.salvage.contains("also returns"))

        val heir = run.aliveHeroes().first()
        val order = Commission(CommissionId("heirloom"), heir.id, fine.familyId, 1, 40, run.day, run.day + 3, CommissionStatus.ACCEPTED, kind = CommissionKind.HEIRLOOM, weaponId = fine.id)
        val kept = run.copy(weapons = mapOf(fine.id to fine.copy(promisedTo = order.id)), commissions = run.commissions + (order.id to order))
        assertEquals("Reserved for ${heir.fullName}'s order", engine.shopUi(kept).storage.single().promised)
        assertEquals("Reserved for ${heir.fullName}'s order", engine.itemDetail(kept, fine.id)!!.stock!!.promised)
        val repo = repo(kept)
        val vm = open(repo)
        vm.dispatch(Command.ToggleShelf(fine.id, true, 10)); advanceUntilIdle()
        assertEquals("That weapon is reserved for ${heir.fullName}'s order.", vm.playing().lastError)
        vm.dispatch(Command.Salvage(fine.id)); advanceUntilIdle()
        assertEquals("That weapon is reserved for ${heir.fullName}'s order.", vm.playing().lastError)
        assertEquals(0, repo.commitCount)
    }
}
