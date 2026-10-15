package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.example.blacksmithproject.ui.contractUi
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PartyDraftTest {
    private val engine = GameEngine()
    private fun state(): GameState {
        val start = engine.guildRun(9).copy(gold = 10_000, pendingRelicOffer = emptyList())
        return (engine.handle(start, Command.RecruitHero(start.guild!!.candidates.first().heroId)) as CommandOutcome.Accepted).state
    }
    private fun GameViewModel.playing() = ui.value as UiState.Playing
    private fun TestScope.open(s: GameState, handle: SavedStateHandle = SavedStateHandle(), repo: FakeGameRepository = FakeGameRepository(SaveCodec.encodeRun(s), SaveCodec.encodeLegacy(s.legacy))): GameViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return GameViewModel(engine, GameSession(engine, repo, compute = dispatcher), FakeSettings(), handle, compute = dispatcher).also { advanceUntilIdle() }
    }
    private fun check(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    @Test fun orderedDraftSurvivesDetailsSavedStateAndSubmitsExactlyOnce() = check {
        val s = state()
        val repo = FakeGameRepository(SaveCodec.encodeRun(s), SaveCodec.encodeLegacy(s.legacy))
        val handle = SavedStateHandle()
        val vm = open(s, handle, repo)
        val offer = s.guild!!.offers.first { engine.partyMax(s, it.id) == 3 }
        val (a, b, c) = s.guild!!.members.map { it.heroId }
        vm.openContract(offer.id)
        listOf(a, b, c).forEach(vm::togglePartyMember)
        vm.movePartyMember(c, -1); vm.movePartyMember(c, -1)
        vm.setPartyPosture(Posture.CAUTIOUS)
        vm.openSheet(Sheet.Hero(c)); advanceUntilIdle()
        val expected = vm.playing().partyDraft!!
        assertEquals(listOf(c, a, b), expected.members)
        assertEquals(s, vm.playing().state)
        val kept = handle.keys().associateWith { handle.get<Any?>(it) }
        kept.values.forEach { assertTrue(it == null || it is String || it is Int || it is ArrayList<*>) }
        val restored = open(s, SavedStateHandle(kept), repo)
        assertEquals(expected, restored.playing().partyDraft)
        assertEquals(Sheet.Hero(c), restored.playing().sheet)
        assertTrue(restored.back()); advanceUntilIdle()
        assertNull(restored.playing().sheet)
        assertEquals(expected, restored.playing().partyDraft)
        val before = repo.commitCount
        restored.confirmParty(); restored.confirmParty(); advanceUntilIdle()
        restored.confirmParty(); advanceUntilIdle()
        val after = restored.playing()
        assertEquals(listOf(c, a, b), after.state.guild!!.planned!!.heroIds)
        assertEquals(Posture.CAUTIOUS, after.state.guild!!.planned!!.posture)
        assertEquals(s.gold - offer.fee, after.state.gold)
        assertEquals(before + 1, repo.commitCount)
        assertNull(after.partyDraft)
        restored.openContract(offer.id); advanceUntilIdle()
        assertEquals(listOf(c, a, b), restored.playing().partyDraft!!.members)
        restored.cancelPartyPlan(); advanceUntilIdle()
        assertEquals(s.gold, restored.playing().state.gold)
        assertNull(restored.playing().state.guild!!.planned)
    }

    @Test fun removalReaddAndBoundaryMovesKeepOtherIntent() = check {
        val s = state()
        val vm = open(s)
        val ids = s.guild!!.members.map { it.heroId }
        vm.openContract(s.guild!!.offers.first().id)
        ids.forEach(vm::togglePartyMember)
        vm.setPartyPosture(Posture.RECKLESS)
        vm.movePartyMember(ids.first(), -1); vm.movePartyMember(ids.last(), 1)
        vm.togglePartyMember(ids[1]); vm.togglePartyMember(ids[1]); advanceUntilIdle()
        assertEquals(listOf(ids[0], ids[2], ids[1]), vm.playing().partyDraft!!.members)
        assertEquals(Posture.RECKLESS, vm.playing().partyDraft!!.posture)
        assertEquals(s.weapons, vm.playing().state.weapons)
        assertTrue(vm.back()); advanceUntilIdle()
        assertNull(vm.playing().partyDraft)
        assertNull(vm.playing().state.guild!!.planned)
        assertEquals(s.gold, vm.playing().state.gold)
    }

    @Test fun loanRoundTripRequiresReviewAndDoesNotRepeatLoanOrFee() = check {
        val original = state()
        val forged = engine.handle(original, Command.Forge(ForgeMode.QUICK, com.tinyblacksmith.core.content.LaunchContent.SWORD, com.tinyblacksmith.core.content.LaunchContent.IRON, com.tinyblacksmith.core.content.LaunchContent.EMBER_RESIN, null, Risk.BALANCED)) as CommandOutcome.Accepted
        val s = forged.state
        val repo = FakeGameRepository(SaveCodec.encodeRun(s), SaveCodec.encodeLegacy(s.legacy))
        val vm = open(s, repo = repo)
        val hero = s.guild!!.members.first().heroId
        val offer = s.guild!!.offers.first()
        vm.openContract(offer.id); vm.togglePartyMember(hero)
        vm.openSheet(Sheet.Hero(hero))
        vm.dispatch(Command.LoanWeapon(hero, forged.forgedWeaponId!!)); advanceUntilIdle()
        vm.closeSheet(); advanceUntilIdle()
        val before = repo.commitCount
        assertEquals(listOf(hero), vm.playing().partyDraft!!.equipmentChanges(vm.playing().state))
        vm.confirmParty(); advanceUntilIdle()
        assertEquals(before, repo.commitCount)
        assertNull(vm.playing().state.guild!!.planned)
        vm.reviewPartyEquipment(); vm.confirmParty(); advanceUntilIdle()
        assertEquals(before + 1, repo.commitCount)
        assertEquals(forged.forgedWeaponId, vm.playing().state.loanOf(hero)!!.id)
        assertEquals(s.gold - offer.fee, vm.playing().state.gold)
    }

    @Test fun unavailableSelectionsRemainVisibleAndCannotSubmitASubset() = check {
        val s = state()
        val ids = s.guild!!.members.take(2).map { it.heroId }
        val offer = s.guild!!.offers.first()
        for (kind in listOf("wounded", "away", "reserved", "dismissed", "gone")) {
            val g = s.guild!!
            val changed = when (kind) {
                "wounded" -> s.copy(guild = g.copy(members = g.members.map { if (it.heroId == ids[0]) it.copy(wound = Wound(WoundKind.INJURED, s.day + 2, "Test injury")) else it }))
                "away" -> s.copy(guild = g.copy(members = g.members.map { if (it.heroId == ids[0]) it.copy(status = MemberStatus.AWAY) else it }))
                "reserved" -> s.copy(guild = g.copy(reserved = listOf(ids[0])))
                "dismissed" -> s.copy(guild = g.copy(members = g.members.filterNot { it.heroId == ids[0] }))
                else -> s.copy(heroes = s.heroes - ids[0])
            }
            val ui = engine.contractUi(changed, offer.id, ids, Posture.BALANCED)!!
            assertNotNull(kind, ui.sendBlocked)
            assertEquals(ids, ui.picks.filter { it.order != null }.sortedBy { it.order }.map { it.heroId })
            assertNotNull(kind, ui.picks.first { it.heroId == ids[0] }.blocked)
        }
        val vm = open(s)
        vm.openContract(offer.id); ids.forEach(vm::togglePartyMember)
        vm.dispatch(Command.ReserveDefender(ids[0], true)); advanceUntilIdle()
        vm.confirmParty(); advanceUntilIdle()
        assertNull(vm.playing().state.guild!!.planned)
        assertEquals(ids, vm.playing().partyDraft!!.members)
    }

    @Test fun expiredOrMissingOfferAndOversizedPartyAreBlocked() {
        val s = state()
        val offer = s.guild!!.offers.first()
        val ids = s.guild!!.members.map { it.heroId }
        assertNull(engine.contractUi(s, "missing", ids, Posture.BALANCED))
        val expired = s.copy(guild = s.guild!!.copy(offers = listOf(offer.copy(expiresDay = s.day - 1))))
        assertTrue(engine.contractUi(expired, offer.id, ids, Posture.BALANCED)!!.sendBlocked!!.contains("left the board"))
        assertNotNull(engine.contractUi(s, offer.id, ids + ids[0], Posture.BALANCED)!!.sendBlocked)
    }
}
