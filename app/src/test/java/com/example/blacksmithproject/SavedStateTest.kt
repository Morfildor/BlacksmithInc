package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.ForgeMode
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
            vm.selectPanel(Panel.FORGE)
            val draft = ForgeDraft(ForgeMode.ADVANCED, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.SAFE, Technique.TEMPER)
            vm.updateDraft { draft }
            advanceUntilIdle()
            val before = vm.ui.value as UiState.Playing
            assertNotNull("the forged blade's card is open", before.revealWeaponId)
            assertEquals(draft, before.draft)

            // Only what a Bundle can hold may be kept: strings, ints and lists of strings.
            val kept = handle.keys().associateWith { handle.get<Any?>(it) }
            kept.values.forEach { v -> assertTrue("$v", v == null || v is String || v is Int || (v is ArrayList<*> && v.all { it == null || it is String })) }

            val after = open(SavedStateHandle(kept)).also { advanceUntilIdle() }.ui.value as UiState.Playing
            assertEquals(Panel.FORGE, after.panel)
            assertEquals(draft, after.draft)
            assertEquals(before.revealWeaponId, after.revealWeaponId)
            assertEquals(before.state, after.state)

            // A process that starts without saved state (a cold launch) starts on Home with an empty draft.
            val cold = open(SavedStateHandle()).also { advanceUntilIdle() }.ui.value as UiState.Playing
            assertEquals(Panel.HOME to ForgeDraft(), cold.panel to cold.draft)
            assertEquals(null, cold.revealWeaponId)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
