package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the system hands back after it killed the process: only the saved-state values, the store, and nothing in memory. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedStateTest {
    private val engine = GameEngine()

    @Test
    fun destinationDraftAndRevealSurviveRecreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val start = engine.newRun(LegacyProfile(), 42L)
            val repo = FakeGameRepository(SaveCodec.encodeRun(start), SaveCodec.encodeLegacy(start.legacy))
            fun open(saved: SavedStateHandle) = GameViewModel(engine, GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler)), FakeSettings(), saved, compute = StandardTestDispatcher(testScheduler))

            val handle = SavedStateHandle()
            val vm = open(handle); advanceUntilIdle()
            vm.dispatch(Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)); advanceUntilIdle()
            vm.selectRecords(RecordsPage.JOURNAL); vm.selectDest(Dest.FORGE)
            val draft = ForgeDraft(ForgeMode.ADVANCED, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE, Technique.TEMPER)
            vm.updateDraft { draft }
            val sheet = Sheet.Hero(start.heroes.keys.first())
            vm.openSheet(sheet)
            advanceUntilIdle()
            val before = vm.ui.value as UiState.Playing
            assertNotNull("the forged blade's card is open", before.revealWeaponId)
            assertEquals(draft, before.draft)

            // Only what a Bundle can hold may be kept: strings, ints and lists of strings.
            val kept = handle.keys().associateWith { handle.get<Any?>(it) }
            kept.values.forEach { v -> assertTrue("$v", v == null || v is String || v is Int || (v is ArrayList<*> && v.all { it == null || it is String })) }

            val reopened = open(SavedStateHandle(kept)).also { advanceUntilIdle() }
            val after = reopened.ui.value as UiState.Playing
            assertEquals(Dest.FORGE to RecordsPage.JOURNAL, after.dest to after.records)
            assertEquals("the open hero sheet comes back", sheet, after.sheet)
            // Back closes the sheet before it leaves the destination.
            assertTrue(reopened.back()); advanceUntilIdle()
            assertEquals(null to Dest.FORGE, (reopened.ui.value as UiState.Playing).let { it.sheet to it.dest })
            assertEquals(draft, after.draft)
            assertEquals(before.revealWeaponId, after.revealWeaponId)
            assertEquals(before.state, after.state)

            // A process that starts without saved state (a cold launch) starts on Shop with an empty draft.
            val cold = open(SavedStateHandle()).also { advanceUntilIdle() }.ui.value as UiState.Playing
            assertEquals(Dest.SHOP to ForgeDraft(), cold.dest to cold.draft)
            assertEquals(null, cold.revealWeaponId)
            assertEquals(null, cold.sheet)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** A draft kept by the build before the want's slot (ten values) still comes back whole, with no want; one with it keeps it. */
    @Test
    fun aDraftKeptBeforeTheWantSlotStillRestores() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val start = engine.newRun(LegacyProfile(), 42L)
            val repo = FakeGameRepository(SaveCodec.encodeRun(start), SaveCodec.encodeLegacy(start.legacy))
            fun open(saved: SavedStateHandle) = GameViewModel(engine, GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler)), FakeSettings(), saved, compute = StandardTestDispatcher(testScheduler))
            val old = arrayListOf<String?>("ADVANCED", "sword", "iron", "ember_resin", null, "SAFE", null, "c7", "false", "false")
            val restored = open(SavedStateHandle(mapOf("draft" to old))).also { advanceUntilIdle() }.ui.value as UiState.Playing
            assertEquals(ForgeDraft(ForgeMode.ADVANCED, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE, null, CommissionId("c7")), restored.draft)
            val hero = start.heroes.keys.first()
            val handle = SavedStateHandle()
            val vm = open(handle).also { advanceUntilIdle() }
            vm.updateDraft { it.copy(familyId = LaunchContent.SWORD, wantHeroId = hero) }; advanceUntilIdle()
            val kept = handle.keys().associateWith { handle.get<Any?>(it) }
            assertEquals(hero, (open(SavedStateHandle(kept)).also { advanceUntilIdle() }.ui.value as UiState.Playing).draft.wantHeroId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** The Guild part chosen today comes back with the process; the siege strip goes to the Wall over it, as an explicit way there. */
    @Test
    fun theGuildPartChosenTodaySurvivesRecreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val start = engine.guildRun(7)
            val repo = FakeGameRepository(SaveCodec.encodeRun(start), SaveCodec.encodeLegacy(start.legacy))
            fun open(saved: SavedStateHandle) = GameViewModel(engine, GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler)), FakeSettings(), saved, compute = StandardTestDispatcher(testScheduler))
            val handle = SavedStateHandle()
            val vm = open(handle); advanceUntilIdle()
            vm.selectDest(Dest.TOWN); vm.selectGuildTab(GuildTab.CHARTER); advanceUntilIdle()
            assertEquals(GuildTabMemory(GuildTab.CHARTER, start.day), (vm.ui.value as UiState.Playing).guildTab)
            val reopened = open(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })).also { advanceUntilIdle() }
            val after = reopened.ui.value as UiState.Playing
            assertEquals(Dest.TOWN to GuildTabMemory(GuildTab.CHARTER, start.day), after.dest to after.guildTab)
            reopened.selectDest(Dest.SHOP); reopened.openGuildWall(); advanceUntilIdle()
            assertEquals(Dest.TOWN to GuildTab.WALL, (reopened.ui.value as UiState.Playing).let { it.dest to it.guildTab?.tab })
        } finally {
            Dispatchers.resetMain()
        }
    }
}
