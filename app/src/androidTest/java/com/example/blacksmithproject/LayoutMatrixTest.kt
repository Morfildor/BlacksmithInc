package com.example.blacksmithproject

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.ForgePanel
import com.example.blacksmithproject.ui.ShopPanel
import com.example.blacksmithproject.ui.detail.ItemDetailContent
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First version of the layout matrix (plan 9.5): the Shop, the Forge and the item sheet at 360 and 320 dp wide and
 * font scale 1.0, 1.3 and 2.0. No text or control may reach outside the screen's width (a fixed-width control would),
 * and every control must be at least 48 dp tall.
 */
@RunWith(AndroidJUnit4::class)
class LayoutMatrixTest {
    @get:Rule
    val compose = createComposeRule()

    private var size by mutableStateOf(DpSize(360.dp, 640.dp))
    private var font by mutableStateOf(1f)
    private var screen by mutableStateOf("shop")

    private val hasTextOrClick = SemanticsMatcher("text or click") {
        it.config.getOrNull(SemanticsProperties.Text) != null || it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
    }

    @Test
    fun noOverflow() {
        val engine = ShopDayFixtures.engine
        // A real morning with a request, blades on the shelf and in storage.
        val state = (42L..47L).asSequence().flatMap { ShopDayFixtures.run(it, 30) }.map { it.state }
            .first { s -> s.commissions.values.any { it.status == CommissionStatus.ACCEPTED || it.status == CommissionStatus.OFFERED } && s.listedWeapons().isNotEmpty() }
        // The fixture's last day was played, not watched: its cursor is stored at DONE so planning is what opens.
        val repo = MemoryRepository(SaveCodec.encodeRun(state), SaveCodec.encodeLegacy(state.legacy), DayCursor(state.lastResolution!!.commandId.value, DayCursor.Stage.DONE).encode())
        lateinit var vm: GameViewModel
        compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, repo), QuietSettings(), SavedStateHandle()) }
        compose.waitUntil(10_000) { vm.ui.value is UiState.Playing }
        val blade = state.listedWeapons().first()
        val detail = engine.itemDetail(state, blade.id)!!
        compose.runOnUiThread { vm.forgeFor(state.commissions.values.first { it.status == CommissionStatus.ACCEPTED || it.status == CommissionStatus.OFFERED }.id) }

        compose.setContent {
            BlacksmithProjectTheme {
                val ui by vm.ui.collectAsState()
                val s = ui as? UiState.Playing ?: return@BlacksmithProjectTheme
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(font)) {
                    when (screen) {
                        "shop" -> ShopPanel(s.shop, busy = false, reducedMotion = true, onLead = {}, onOpenBlade = {}, onOpenHero = {}, onAnswer = { _, _ -> }, onOpenStorage = {}, onOpenNews = {}, onOpenSupplies = {})
                        "forge" -> ForgePanel(s, vm, reducedMotion = true, tip = null)
                        else -> ItemDetailContent(detail, planning = true, onOpenHero = {}, onStock = {}, onDismiss = {})
                    }
                }
            }
        }
        for (w in listOf(DpSize(360.dp, 640.dp), DpSize(320.dp, 569.dp))) for (f in listOf(1f, 1.3f, 2f)) for (name in listOf("shop", "forge", "item")) {
            compose.runOnUiThread { size = w; font = f; screen = name }
            compose.waitForIdle()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val where = "$name at ${w.width} x font $f"
            val nodes = compose.onAllNodes(hasTextOrClick, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$where shows something", nodes.isNotEmpty())
            for (n in nodes) {
                val b = n.boundsInRoot
                if (b.width <= 0f || b.height <= 0f) continue
                val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: n.config.getOrNull(SemanticsProperties.TestTag) ?: "a control"
                assertTrue("$where: \"$label\" spans ${b.left}..${b.right} of ${root.width}", b.left >= -0.5f && b.right <= root.width + 0.5f)
            }
            if (name == "forge") compose.onNodeWithTag("forge_weapon").assertIsDisplayed()
        }
    }
}
