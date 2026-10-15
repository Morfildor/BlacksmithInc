package com.example.blacksmithproject

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blacksmithproject.ui.TodayRows
import com.example.blacksmithproject.ui.TodayTarget
import com.example.blacksmithproject.ui.TodayUi
import com.example.blacksmithproject.ui.WorkshopScreen
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                        "shop" -> ShopPanel(s.shop, busy = false, reducedMotion = true, onLead = {}, onOpenBlade = {}, onOpenBoard = {}, onOpenStorage = {}, onOpenNews = {}, onOpenSupplies = {})
                        "forge" -> ForgePanel(s, vm, reducedMotion = true, tip = null)
                        else -> ItemDetailContent(detail, planning = true, onOpenHero = {}, onStock = {}, onDismiss = {})
                    }
                }
            }
        }
        for (w in listOf(DpSize(360.dp, 640.dp), DpSize(320.dp, 569.dp))) for (f in listOf(1f, 1.3f, 1.5f, 2f)) for (name in listOf("shop", "forge", "item")) {
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
    private fun assertFullText(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("text layout exists for $tag", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse("$tag has visual overflow", layout.hasVisualOverflow)
            for (line in 0 until layout.lineCount) assertFalse("$tag ellipsized", layout.isLineEllipsized(line))
        }
    }

    @Test
    fun longTodayRemindersAndActionsRemainReadableAndReachable() {
        val rows = listOf(
            TodayUi("rescue", "Alexandria of the Northern Mountain Stronghold is held. Today is the last day to send a rescue party before the opportunity closes.", "Review the rescue and departure deadline", TodayTarget.Tab(GuildTab.CONTRACTS, "rescue")),
            TodayUi("checkpoint", "The expedition waits at the deeper door. Without word, everyone returns home with the rewards already secured.", "Decide whether the expedition returns", TodayTarget.Tab(GuildTab.CONTRACTS, "party")),
            TodayUi("wall", "A defender place is unfilled because the planned expedition is away during the siege. Review who can remain at home.", "Review available siege defenders", TodayTarget.AssignWall),
        )
        var chosen: TodayUi? = null
        compose.setContent {
            BlacksmithProjectTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(font)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { TodayRows(rows) { chosen = it } }
                }
            }
        }
        for (width in listOf(320, 360, 600)) for (scale in listOf(1f, 1.5f, 2f)) {
            compose.runOnUiThread { size = DpSize(width.dp, 700.dp); font = scale }
            compose.waitForIdle()
            rows.forEach { row ->
                compose.onNodeWithTag("guild_today_text_${row.key}").performScrollTo()
                assertFullText("guild_today_text_${row.key}")
                compose.onNodeWithTag("guild_today_go_${row.key}").performScrollTo().assertIsDisplayed().performClick()
                assertEquals(row, chosen)
                val actionText = compose.onNodeWithText("${row.action}  ›", useUnmergedTree = true)
                val actionLayouts = mutableListOf<TextLayoutResult>()
                actionText.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(actionLayouts) }
                assertTrue(actionLayouts.isNotEmpty())
                val bounds = actionText.fetchSemanticsNode().boundsInRoot
                val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
                // Intrinsic button sizing can leave paragraph.width larger than size.width with every glyph visible.
                // Check painted lines and ellipsis against the viewport, rather than that width comparison.
                actionLayouts.forEach { layout ->
                    for (line in 0 until layout.lineCount) {
                        assertFalse("action ellipsized at $width dp / $scale", layout.isLineEllipsized(line))
                        assertTrue("action clips left at $width dp / $scale", bounds.left + layout.getLineLeft(line) >= viewport.left - 1f)
                        assertTrue("action clips right at $width dp / $scale", bounds.left + layout.getLineRight(line) <= viewport.right + 1f)
                    }
                }
            }
        }
    }

    /** Real saved mornings, full workshop chrome and direct Today navigation, with screenshots in ignored output. */
    @Test
    fun siegeScreensAndTodayNavigationAtRequestedFontScales() {
        val engine = ShopDayFixtures.engine
        val classic = ShopDayFixtures.run(42, 30).map { it.state }.first { it.day == it.town.nextSiegeDay && !it.isEnded }
        val opening = engine.newRun(LegacyProfile(), 7, charterId = GuildContent.TOWNS_LAST_HOPE)
        var guild = (engine.handle(opening, Command.ChooseOpening(opening.guild!!.openingOffer.first().defId)) as CommandOutcome.Accepted).state
        val bot = GuildBot(engine, GuildPolicy.NOVICE)
        while (guild.day < guild.town.nextSiegeDay) {
            val planned = bot.planDay(guild)
            guild = (engine.handle(planned, Command.EndDay(CommandId("layout:${planned.day}"))) as CommandOutcome.Accepted).state
        }
        val states = listOf(classic, guild).map { it.copy(pendingRelicOffer = emptyList(), pendingBlessingOffer = emptyList(), encounter = null) }
        val vms = states.map { state ->
            val cursor = state.lastResolution?.let { DayCursor(it.commandId.value, DayCursor.Stage.DONE).encode() }
            val repo = MemoryRepository(SaveCodec.encodeRun(state), SaveCodec.encodeLegacy(state.legacy), cursor)
            lateinit var vm: GameViewModel
            compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, repo), QuietSettings(), SavedStateHandle()) }
            compose.waitUntil(10_000) { vm.ui.value is UiState.Playing }
            vm
        }
        compose.setContent {
            val vm = if (screen == "classic") vms[0] else vms[1]
            val ui by vm.ui.collectAsState()
            BlacksmithProjectTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(font)) {
                    (ui as? UiState.Playing)?.let { WorkshopScreen(it, vm) }
                }
            }
        }
        val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "t1-evidence").apply { mkdirs() }
        // A shell-selected scale also reaches the modal window, which does not inherit ForcedSize's density.
        val systemScale = InstrumentationRegistry.getArguments().getString("readinessFontScale")?.toFloat()
        for (scale in systemScale?.let { listOf(it) } ?: listOf(1f, 1.5f, 2f)) {
            compose.runOnUiThread { size = DpSize(360.dp, 800.dp); font = scale; screen = "classic"; vms[0].selectDest(Dest.SHOP) }
            compose.waitForIdle()
            assertFullText("siege_countdown")
            compose.onNodeWithTag("shop_lead", useUnmergedTree = true).assertIsDisplayed()
            File(evidence, "classic-$scale.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.runOnUiThread { screen = "guild"; vms[1].selectGuildTab(GuildTab.WALL); vms[1].selectDest(Dest.TOWN) }
            compose.waitForIdle()
            assertFullText("siege_countdown")
            compose.onNodeWithTag("guild_tab_wall").assertIsDisplayed()
            compose.onNodeWithTag("guild_list").performScrollToIndex(0)
            File(evidence, "guild-$scale.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val row = (vms[1].ui.value as UiState.Playing).guild!!.today.firstOrNull { it.target is TodayTarget.Tab }
            if (row != null) {
                compose.onNodeWithTag("guild_list").performScrollToNode(hasTestTag("guild_today_go_${row.key}"))
                assertFullText("guild_today_text_${row.key}")
                compose.onNodeWithTag("guild_today_go_${row.key}").performClick()
                compose.waitForIdle()
                val target = row.target as TodayTarget.Tab
                assertEquals(target.tab, (vms[1].ui.value as UiState.Playing).guildTab!!.tab)
                File(evidence, "guild-answers-$scale.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithTag("guild_list").performScrollToNode(hasTestTag("guild_wall_assign"))
            compose.onNodeWithTag("guild_wall_assign").performClick()
            compose.onNodeWithTag("guild_assign_sheet").assertIsDisplayed()
            File(evidence, "guild-assign-$scale.png").outputStream().use { compose.onNodeWithTag("guild_assign_sheet").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val member = states[1].guild!!.members.first().heroId
            compose.onNodeWithText(states[1].heroes.getValue(member).fullName).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(Sheet.Hero(member), (vms[1].ui.value as UiState.Playing).sheet) }
            compose.runOnUiThread { vms[1].closeSheet() }
            compose.onNodeWithTag("guild_assign_done").performScrollTo().performClick()
        }
    }

}
