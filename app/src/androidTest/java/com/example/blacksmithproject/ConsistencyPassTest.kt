package com.example.blacksmithproject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.RecordsPanel
import com.example.blacksmithproject.ui.TownPanel
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The consistency pass (UI/UX improvement 4): the Records tabs and the Gazette row, Town's outlook and its champion slots. */
@RunWith(AndroidJUnit4::class)
class ConsistencyPassTest {
    @get:Rule
    val compose = createComposeRule()

    private val engine = GameEngine()

    private fun show(dest: Dest, panel: @Composable (UiState.Playing, GameViewModel) -> Unit) {
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = MemoryRepository(SaveCodec.encodeRun(start), SaveCodec.encodeLegacy(start.legacy))
        lateinit var vm: GameViewModel
        compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, repo), QuietSettings(), SavedStateHandle()) }
        compose.setContent {
            BlacksmithProjectTheme {
                val ui by vm.ui.collectAsState()
                (ui as? UiState.Playing)?.let { Box(Modifier.size(360.dp, 640.dp)) { panel(it, vm) } }
            }
        }
        compose.waitUntil(10_000) { vm.ui.value is UiState.Playing }
        compose.runOnUiThread { vm.selectDest(dest) }
        compose.waitForIdle()
    }

    /** The paper's page is called what every link to it says. */
    @Test
    fun theGazettePageIsNamedGazette() {
        show(Dest.RECORDS) { s, vm -> RecordsPanel(s, vm) }
        compose.onNodeWithTag("page_gazette").assertIsSelected().assertTextEquals("Gazette")

    }

    /** An empty champion place says why, by the engine's rule. */
    @Test
    fun townExplainsItsEmptyChampionPlaces() {
        show(Dest.TOWN) { s, vm -> TownPanel(s, vm) }
        // The threat, the outlook and the champions are one item of the list, so the slots are composed with the header.
        val fit = engine.config.heroWoundedThreshold
        compose.onNodeWithText("health $fit or more", substring = true).assertExists()
        (0..2).forEach { compose.onNodeWithTag("town_champion_empty_$it", useUnmergedTree = true).assertTextEquals("Not named yet: champions are chosen at the first End Day.") }
        assertEquals("day 1 has no champions in the save", 0, engine.newRun(LegacyProfile(), 42L).town.championIds.size)
    }
}
