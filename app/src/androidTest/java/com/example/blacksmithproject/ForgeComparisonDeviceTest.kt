package com.example.blacksmithproject

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blacksmithproject.ui.ForgePanel
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Want
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T2 on a device: the Forge's purpose brief, the open tray's comparison, a blocked material and a guild run's effects,
 * at 320 and 360 dp and font scale 1.0, 1.5 and 2.0. Nothing reaches outside the screen's width, the Forge action and
 * the risk beside it stay on screen, the tray stays open through A, B and "Put back", and no command is sent. The
 * screenshots go to the app's external files, `t2-evidence`.
 */
@RunWith(AndroidJUnit4::class)
class ForgeComparisonDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private val engine = ShopDayFixtures.engine
    private var size by mutableStateOf(DpSize(360.dp, 800.dp))
    private var font by mutableStateOf(1f)
    /** A new value starts the panel over (its open tray and baseline are its own state). */
    private var round by mutableIntStateOf(0)
    private val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "t2-evidence").apply { mkdirs() }
    private val hasTextOrClick = SemanticsMatcher("text or click") {
        it.config.getOrNull(SemanticsProperties.Text) != null || it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
    }

    private fun open(state: GameState): GameViewModel {
        val cursor = state.lastResolution?.let { DayCursor(it.commandId.value, DayCursor.Stage.DONE).encode() }
        val repo = MemoryRepository(SaveCodec.encodeRun(state), SaveCodec.encodeLegacy(state.legacy), cursor)
        lateinit var vm: GameViewModel
        compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, repo), QuietSettings(), SavedStateHandle()) }
        compose.waitUntil(10_000) { vm.ui.value is UiState.Playing }
        compose.setContent {
            val ui by vm.ui.collectAsState()
            BlacksmithProjectTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(font)) {
                    key(round) { (ui as? UiState.Playing)?.let { ForgePanel(it, vm, reducedMotion = true, tip = null) } }
                }
            }
        }
        return vm
    }

    private fun GameViewModel.playing() = ui.value as UiState.Playing
    private fun shot(name: String) = File(evidence, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun assertInside(where: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        for (n in compose.onAllNodes(hasTextOrClick, useUnmergedTree = true).fetchSemanticsNodes()) {
            val b = n.boundsInRoot
            if (b.width <= 0f || b.height <= 0f) continue
            val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: n.config.getOrNull(SemanticsProperties.TestTag) ?: "a control"
            assertTrue("$where: \"$label\" spans ${b.left}..${b.right} of ${root.width}", b.left >= -0.5f && b.right <= root.width + 0.5f)
        }
        // The pinned foot: the action and the temper beside it are always on screen.
        compose.onNodeWithTag("forge_weapon").assertIsDisplayed()
        compose.onNodeWithTag("forge_risk").assertIsDisplayed()
    }

    /** A text somewhere under the node tagged [tag], scrolled into view: the tagged containers merge no text of their own. */
    private fun textIn(tag: String, text: String) =
        compose.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).performScrollTo().assertExists()

    private val matrix = listOf(320, 360).flatMap { w -> listOf(1f, 1.5f, 2f).map { w to it } }

    private fun stocked(state: GameState, category: MaterialCategory) = engine.content.materials(category).filter { (state.materials[it.id] ?: 0) > 0 }

    @Test
    fun aCommissionsBriefStockAndAnOpenComparisonAtEveryScale() {
        val morning = (42L..47L).asSequence().flatMap { ShopDayFixtures.run(it, 30) }.map { it.state }
            .first { s -> s.commissions.values.any { it.status == CommissionStatus.ACCEPTED || it.status == CommissionStatus.OFFERED } && s.listedWeapons().isNotEmpty() && stocked(s, MaterialCategory.CORE).size >= 2 }
        val state = morning.copy(pendingRelicOffer = emptyList(), pendingBlessingOffer = emptyList(), encounter = null)
        val asked = state.commissions.values.first { it.status == CommissionStatus.ACCEPTED || it.status == CommissionStatus.OFFERED }
        val vm = open(state)
        compose.runOnUiThread { vm.forgeFor(asked.id) }
        val a = stocked(state, MaterialCategory.CORE)[0].id
        val b = stocked(state, MaterialCategory.CORE)[1].id
        val augment = vm.playing().draft.augmentId ?: stocked(state, MaterialCategory.AUGMENT).first().id
        compose.runOnUiThread { vm.updateDraft { it.copy(coreId = a, augmentId = augment) } }
        compose.waitForIdle()
        val start = vm.playing().draft
        val saved = vm.playing().state
        for ((w, f) in matrix) {
            compose.runOnUiThread { size = DpSize(w.dp, 800.dp); font = f; round++; vm.updateDraft { start } }
            compose.waitForIdle()
            val where = "classic at $w dp x $f"
            compose.onNodeWithTag("forge_for").assertIsDisplayed()
            compose.onNodeWithTag("forge_for_stock").performScrollTo()
            assertInside("$where, brief")
            shot("classic-brief-$w-$f")
            // Open the metal tray on A and try B: the tray stays open and says what changed.
            compose.onNodeWithTag("forge_slot_metal").performScrollTo().performClick()
            compose.onNodeWithTag("forge_option_${b.value}").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(where, b, vm.playing().draft.coreId)
            compose.onNodeWithTag("forge_tray_metal").assertExists()
            textIn("forge_tray_change", "Compared with ${engine.content.material(a).name}")
            assertInside("$where, comparing")
            shot("classic-compare-$w-$f")
            // "Put back": the first draft exactly, the tray still open, nothing to compare.
            compose.onNodeWithTag("forge_tray_put_back").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(where, start, vm.playing().draft)
            textIn("forge_tray_change", "Unchanged from")
            compose.onNodeWithTag("forge_tray_done").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("forge_tray_metal").assertDoesNotExist()
        }
        assertEquals("browsing sent nothing", saved, vm.playing().state)
    }

    @Test
    fun aMaterialThatRanOutIsShownWithWhyAndNeverPlaced() {
        val fresh = engine.newRun(LegacyProfile(), 42L)
        val cores = stocked(fresh, MaterialCategory.CORE)
        val out = cores.last().id
        val state = fresh.copy(materials = fresh.materials + (out to 0))
        val vm = open(state)
        val start = ForgeDraft(familyId = engine.content.families.first().id, coreId = cores.first().id, augmentId = stocked(state, MaterialCategory.AUGMENT).first().id)
        compose.runOnUiThread { vm.updateDraft { start } }
        for ((w, f) in listOf(360 to 1f, 320 to 2f)) {
            compose.runOnUiThread { size = DpSize(w.dp, 800.dp); font = f; round++ }
            compose.waitForIdle()
            compose.onNodeWithTag("forge_slot_metal").performScrollTo().performClick()
            compose.onNodeWithTag("forge_option_${out.value}").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNode(hasText("None left.", substring = true) and hasTestTag("forge_tray_blocked"), useUnmergedTree = true).performScrollTo().assertExists()
            assertEquals("a blocked tile is only inspected", start, vm.playing().draft)
            assertInside("blocked at $w dp x $f")
            shot("classic-blocked-$w-$f")
        }
    }

    @Test
    fun aWantIsBriefedAsNoOrder() {
        val fresh = engine.newRun(LegacyProfile(), 42L)
        val hero = fresh.aliveHeroes().first()
        val family = engine.content.families.first().id
        val state = fresh.copy(heroes = fresh.heroes + (hero.id to hero.copy(want = Want(family, minPower = 10, budget = 95, sinceDay = fresh.day))))
        val vm = open(state)
        compose.runOnUiThread { vm.forgeWant(hero.id) }
        for ((w, f) in listOf(360 to 1f, 360 to 2f, 320 to 1.5f)) {
            compose.runOnUiThread { size = DpSize(w.dp, 800.dp); font = f; round++ }
            compose.waitForIdle()
            compose.onNodeWithTag("forge_for").assertIsDisplayed()
            textIn("forge_for", "Not an order")
            assertInside("want at $w dp x $f")
            shot("classic-want-$w-$f")
        }
        assertTrue(vm.playing().state.commissions.isEmpty() || vm.playing().state.commissions == state.commissions)
    }

    @Test
    fun aGuildRunsFirstChoiceAndComparisonCarryTheirEffects() {
        val opening = engine.newRun(LegacyProfile(), 7, charterId = GuildContent.TOWNS_LAST_HOPE)
        val guild = (engine.handle(opening, Command.ChooseOpening(opening.guild!!.openingOffer.first().defId)) as CommandOutcome.Accepted).state
            .let { it.copy(pendingRelicOffer = emptyList(), pendingBlessingOffer = emptyList(), encounter = null) }
        val vm = open(guild)
        val augments = stocked(guild, MaterialCategory.AUGMENT)
        val a = augments.first()
        val b = augments.first { it.element != a.element }
        val start = ForgeDraft(familyId = engine.content.families.first().id, coreId = stocked(guild, MaterialCategory.CORE).first().id, augmentId = a.id)
        for ((w, f) in matrix) {
            // The first choice of an empty place lists what each metal does in a fight.
            compose.runOnUiThread { size = DpSize(w.dp, 800.dp); font = f; vm.updateDraft { start.copy(coreId = null) }; round++ }
            compose.waitForIdle()
            compose.onNodeWithTag("forge_tray_fight").performScrollTo()
            assertInside("guild first choice at $w dp x $f")
            shot("guild-first-choice-$w-$f")
            compose.runOnUiThread { vm.updateDraft { start }; round++ }
            compose.waitForIdle()
            compose.onNodeWithTag("forge_slot_augment").performScrollTo().performClick()
            compose.onNodeWithTag("forge_option_${b.id.value}").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(b.id, vm.playing().draft.augmentId)
            textIn("forge_tray_change", "Compared with ${a.name}")
            assertInside("guild comparing at $w dp x $f")
            shot("guild-compare-$w-$f")
        }
    }
}
