package com.example.blacksmithproject

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blacksmithproject.data.IntroRecord
import com.example.blacksmithproject.data.IntroStatus
import com.example.blacksmithproject.ui.IntroCardLine
import com.example.blacksmithproject.ui.IntroLine
import com.example.blacksmithproject.ui.IntroRowUi
import com.example.blacksmithproject.ui.IntroStep
import com.example.blacksmithproject.ui.IntroStrip
import com.example.blacksmithproject.ui.IntroUi
import com.example.blacksmithproject.ui.SettingsSheet
import com.example.blacksmithproject.ui.TinyBlacksmithApp
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.model.LegacyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The guided first day on a screen: the strip is part of the layout (the controls under it take their taps), it fits
 * the layout matrix's widths and font scales, and "Skip guidance" is there on the strip and on the forge result.
 */
@RunWith(AndroidJUnit4::class)
class IntroStripTest {
    @get:Rule
    val compose = createComposeRule()

    private val line = IntroLine(
        "Choose a weapon, a metal and an augment.",
        "It costs 2 of your 10 energy and one of each ingredient. Quality lands near what the recipe gives, sometimes above and sometimes below.",
        "A costlier metal raises quality and power. Every pairing has a fit you cannot see yet; your field notes fill in as you forge.",
    )

    @Test
    fun theStripAndTheCardLineFitEveryWidthAndFontScale() {
        var size by mutableStateOf(DpSize(360.dp, 640.dp))
        var font by mutableStateOf(1f)
        var card by mutableStateOf(false)
        compose.setContent {
            BlacksmithProjectTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(font)) {
                    // In a column, as in the workshop: the forced screen would otherwise stretch the strip to its own height.
                    Column { if (card) IntroCardLine(line, onSkip = {}) else IntroStrip(IntroUi(IntroStep.FORGE, "FIRST DAY · 2 of 5", line), onSkip = {}, onDone = {}) }
                }
            }
        }
        val textOrClick = SemanticsMatcher("text or click") { it.config.getOrNull(SemanticsProperties.Text) != null || it.config.contains(SemanticsActions.OnClick) }
        for (w in listOf(DpSize(360.dp, 640.dp), DpSize(320.dp, 569.dp))) for (f in listOf(1f, 1.3f, 2f)) for (which in listOf(false, true)) {
            compose.runOnUiThread { size = w; font = f; card = which }
            compose.waitForIdle()
            // "Why?" open: the longest the strip gets.
            compose.onAllNodes(hasTestTag("intro_why"))[0].performClick()
            compose.waitForIdle()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val density = compose.density.density
            for (n in compose.onAllNodes(textOrClick, useUnmergedTree = true).fetchSemanticsNodes()) {
                val b = n.boundsInRoot
                if (b.width <= 0f || b.height <= 0f) continue   // scrolled or clipped out of the forced screen
                val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: "a control"
                assertTrue("${w.width} x font $f: \"$label\" spans ${b.left}..${b.right} of ${root.width}", b.left >= -0.5f && b.right <= root.width + 0.5f)
                if (n.config.contains(SemanticsActions.OnClick)) assertTrue("${w.width} x font $f: \"$label\" is ${b.height / density} dp tall", b.height / density >= 47.5f)
            }
            compose.onAllNodes(hasTestTag("intro_skip"))[0].assertIsDisplayed()
            // However much is open, the strip leaves the larger part of the screen to the game. Its limit is a share of
            // the device's own screen (the forced size does not change the configuration it reads), so that is the measure.
            if (!which) {
                val strip = compose.onNodeWithTag("intro_strip").fetchSemanticsNode().boundsInRoot.height
                val screen = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.heightPixels
                assertTrue("${w.width} x font $f: the strip is $strip of $screen", strip <= screen * 0.45f)
            }
            compose.onAllNodes(hasTestTag("intro_why"))[0].performClick()
        }
    }

    @Test
    fun aNewInstallationsFirstGameShowsTheStepLeavesTheControlsFreeAndCanBeSkippedFromTheResult() {
        val engine = GameEngine()
        val settings = QuietSettings(IntroRecord())
        lateinit var vm: GameViewModel
        compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, MemoryRepository(null, SaveCodec.encodeLegacy(LegacyProfile()))), settings, SavedStateHandle()) }
        compose.setContent { BlacksmithProjectTheme { TinyBlacksmithApp(vm) } }
        compose.waitUntil(15_000) { vm.ui.value is UiState.Title }
        // The title says the game about to start is guided, with the way out beside it, and opens on the shop alone.
        compose.onNodeWithTag("intro_menu_skip").assertIsDisplayed()
        compose.onNodeWithTag("charter_name", useUnmergedTree = true).assertTextEquals("No charter")
        compose.onNodeWithTag("title_new_run").performClick()
        compose.waitUntil(15_000) { (vm.ui.value as? UiState.Playing)?.intro != null }

        compose.onNodeWithTag("intro_strip").assertIsDisplayed()
        compose.onNodeWithTag("intro_objective", useUnmergedTree = true).assertTextEquals("Choose a weapon, a metal and an augment.")
        // No relic dialog over it, and the bar under the strip takes its tap.
        compose.onNodeWithTag("nav_forge").performClick()
        compose.waitForIdle()
        for (id in listOf("sword", "iron", "ember_resin")) { compose.onNodeWithTag("forge_option_$id").performClick(); compose.waitForIdle() }
        compose.onNodeWithTag("intro_objective", useUnmergedTree = true).assertTextEquals("Forge it.")
        compose.onNodeWithTag("forge_weapon").performClick()
        compose.waitUntil(15_000) { (vm.ui.value as? UiState.Playing)?.revealWeaponId != null }

        // The result covers the strip: it says the step itself, and the way out is on it.
        compose.onNodeWithTag("intro_card").assertIsDisplayed()
        compose.onNodeWithTag("reveal_list").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("intro_skip"))[0].performClick()
        compose.waitUntil(15_000) { (vm.ui.value as? UiState.Playing)?.intro == null }
        compose.onNodeWithTag("intro_card").assertDoesNotExist()
        compose.onNodeWithTag("intro_strip").assertDoesNotExist()
        compose.onNodeWithTag("reveal_list").assertIsDisplayed()
        assertEquals(IntroStatus.SKIPPED, settings.introRecord.status)
    }

    @Test
    fun settingsShowsWhereTheGuidanceStandsAndItsOneAction() {
        var asked = 0
        compose.setContent { BlacksmithProjectTheme { SettingsSheet(false, {}, true, {}, onDismiss = {}, intro = IntroRowUi("Starts with your next new game.", "Cancel"), onIntro = { asked++ }) } }
        compose.onNodeWithTag("settings_intro_status").assertTextEquals("Starts with your next new game.")
        compose.onNodeWithTag("settings_intro_action").assertTextEquals("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(1, asked)
    }
}
