package com.example.blacksmithproject

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Risk
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blacksmithproject.data.SaveStore
import com.example.blacksmithproject.ui.TinyBlacksmithApp
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole app over its own Room file: what stays where it was while the player moves between destinations, sheets,
 * the main menu and an unwatched day, and where system Back leads. The ViewModel's side of it is `NavigationTest`.
 */
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val engine = GameEngine()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: SaveStore

    @Before
    fun clean() { context.deleteDatabase(DB) }

    @After
    fun cleanUp() { context.deleteDatabase(DB) }

    /** The app on [run], planning (its last day watched), past the main menu. */
    private fun play(run: GameState): GameViewModel {
        store = SaveStore.create(context, DB)
        runBlocking {
            store.commit(SaveCodec.encodeRun(run.copy(pendingRelicOffer = emptyList())), SaveCodec.encodeLegacy(run.legacy))   // the opening relic offer answered: its dialog is not what is under test
            run.lastResolution?.let { store.saveCursor(DayCursor(it.commandId.value, DayCursor.Stage.DONE).encode()) }
        }
        lateinit var vm: GameViewModel
        compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, store), QuietSettings(), SavedStateHandle()) }
        compose.setContent { BlacksmithProjectTheme { TinyBlacksmithApp(vm) } }
        compose.waitUntil(15_000) { vm.ui.value is UiState.Playing }
        compose.onNodeWithTag("menu_continue").performClick()
        compose.waitForIdle()
        return vm
    }

    /** A played run with three more blades in storage, so the Shop scrolls and Storage has something to order. */
    private fun stocked(): GameState {
        val played = ShopDayFixtures.run(42, 2).last().state
        val blade = played.weapons.values.first()
        return played.copy(weapons = played.weapons + (1..3).associate { i -> WeaponId("t$i").let { it to blade.copy(id = it, name = "Blade $i", location = WeaponLocation.Storage, power = blade.power + i) } })
    }

    private fun back() { Espresso.pressBack(); compose.waitForIdle() }

    private fun planningRun(): GameState {
        var s = engine.newRun(LegacyProfile(), 9, charterId = GuildContent.TOWNS_LAST_HOPE)
        fun send(c: Command) { s = (engine.handle(s, c) as CommandOutcome.Accepted).state }
        send(Command.ChooseOpening(s.guild!!.openingOffer.first().defId))
        if (s.pendingRelicOffer.isNotEmpty()) send(Command.DeclineRelicOffer)
        s = s.copy(gold = 10_000)
        send(Command.RecruitHero(s.guild!!.candidates.first().heroId))
        send(Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED))
        return s
    }

    /** Actual device configuration: full Forge chrome, labels, hit targets and destination callbacks. */
    @Test
    fun batchANavigationAtCurrentDeviceConfiguration() {
        val vm = play(stocked())
        compose.onNodeWithTag("nav_forge").performClick()
        val config = context.resources.configuration
        val dir = File(context.getExternalFilesDir(null), "batch-a-evidence").apply { mkdirs() }
        File(dir, "forge-${config.screenWidthDp}-${config.fontScale}.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        for (dest in listOf("shop", "forge", "town", "records")) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag("nav_label_$dest", useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$dest label overflow", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
            val b = compose.onNodeWithTag("nav_$dest").fetchSemanticsNode().boundsInRoot
            val density = context.resources.displayMetrics.density
            assertTrue("$dest touch area", b.width >= 48 * density - 1 && b.height >= 48 * density - 1)
            compose.onNodeWithTag("nav_$dest").performClick().assertIsSelected()
        }
        compose.onNodeWithTag("nav_forge").performClick()
        compose.onNodeWithTag("forge_weapon").assertIsDisplayed()
    }

    @Test
    fun guildPartySurvivesEquipmentAndBackWithExactOrderAndOneFee() {
        val run = planningRun()
        val vm = play(run)
        val (a, b, c) = run.guild!!.members.map { it.heroId }
        val offer = run.guild!!.offers.first { engine.partyMax(run, it.id) == 3 }
        compose.onNodeWithTag("nav_town").performClick()
        compose.onNodeWithTag("guild_list").performScrollToNode(hasTestTag("guild_offer_${offer.id}"))
        compose.onNodeWithTag("guild_offer_${offer.id}").performClick()
        for (id in listOf(a, b, c)) compose.onNodeWithTag("contract_pick_${id.value}").performScrollTo().performClick()
        repeat(2) { compose.onNodeWithTag("contract_up_${c.value}").performScrollTo().performClick() }
        compose.onNodeWithTag("contract_posture_cautious").performScrollTo().performClick()
        compose.onNodeWithTag("contract_equipment_${c.value}").performScrollTo().performClick()
        val weapon = run.weapons.values.first { it.isInStorage }
        compose.onNodeWithTag("sheet_loan_${weapon.id.value}").performScrollTo().performClick()
        compose.waitUntil(10_000) { (vm.ui.value as? UiState.Playing)?.state?.loanOf(c)?.id == weapon.id }
        back()
        compose.onNodeWithTag("contract_send").assertIsNotEnabled()
        compose.onNodeWithTag("contract_review_equipment").performScrollTo().performClick()
        compose.onNodeWithTag("contract_send").assertIsEnabled()
        val draft = (vm.ui.value as UiState.Playing).partyDraft!!
        assertEquals(listOf(c, a, b), draft.members)
        assertEquals(Posture.CAUTIOUS, draft.posture)
        // Weapon inspection also replaces the modal and returns to the same authoritative draft.
        compose.runOnUiThread { vm.openSheet(Sheet.Item(weapon.id)) }
        compose.onNodeWithTag("item_sheet").assertIsDisplayed()
        back()
        assertEquals(draft, (vm.ui.value as UiState.Playing).partyDraft)
        compose.onNodeWithTag("contract_send").performClick()
        compose.waitUntil(10_000) { (vm.ui.value as? UiState.Playing)?.partyDraft == null }
        val after = (vm.ui.value as UiState.Playing).state
        assertEquals(listOf(c, a, b), after.guild!!.planned!!.heroIds)
        assertEquals(Posture.CAUTIOUS, after.guild!!.planned!!.posture)
        assertEquals(run.gold - offer.fee, after.gold)
        compose.runOnUiThread { vm.openContract(offer.id) }
        compose.onNodeWithTag("contract_cancel").performScrollTo().performClick()
        compose.waitUntil(10_000) { (vm.ui.value as? UiState.Playing)?.state?.guild?.planned == null }
        assertEquals(run.gold, (vm.ui.value as UiState.Playing).state.gold)
    }

    /** Run with real emulator width/font settings. Evidence includes the board and the sheet's pinned action. */
    @Test
    fun guildPlanningLayoutAtCurrentDeviceConfiguration() {
        var initial = planningRun()
        while (initial.guild!!.members.size < engine.config.guild.maxMembers && initial.guild!!.candidates.isNotEmpty()) {
            initial = (engine.handle(initial, Command.RecruitHero(initial.guild!!.candidates.first().heroId)) as CommandOutcome.Accepted).state
        }
        val longNames = initial.guild!!.members.mapIndexed { i, m -> m.heroId to initial.heroes.getValue(m.heroId).copy(name = "Alexandria of the Northern Mountains ${i + 1}") }.toMap()
        val run = initial.copy(heroes = initial.heroes + longNames, town = initial.town.copy(nextSiegeDay = initial.day))
        val vm = play(run)
        compose.onNodeWithTag("nav_town").performClick()
        compose.onNodeWithTag("guild_tab_contracts").performClick()
        val offer = run.guild!!.offers.first { engine.partyMax(run, it.id) == 3 }
        compose.onNodeWithTag("guild_list").performScrollToNode(hasTestTag("guild_offer_${offer.id}"))
        compose.onNodeWithTag("guild_offer_terms_${offer.id}", useUnmergedTree = true).assertExists()
        val config = context.resources.configuration
        val label = "${config.screenWidthDp}dp-${config.fontScale}"
        val dir = File(context.getExternalFilesDir(null), "t4-evidence").apply { mkdirs() }
        fun shot(tag: String, name: String) { File(dir, "$label-$name.png").outputStream().use { compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) } }
        shot("guild_list", "contracts")
        compose.onNodeWithTag("guild_offer_${offer.id}").performClick()
        for (id in run.guild!!.members.take(3).map { it.heroId }) compose.onNodeWithTag("contract_pick_${id.value}").performScrollTo().performClick()
        compose.onNodeWithTag("contract_send").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("contract_returns").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("contract_siege_warning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("contract_send").assertIsDisplayed()
        // Every text lays out without ellipsis, and reorder controls have at least 48 dp hit areas.
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("contract_siege_warning").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        shot("contract_sheet", "confirmation")
        assertTrue("Warning layouts: ${layouts.map { "${it.size}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}, lines=${it.lineCount}" }}", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        val id = run.guild!!.members[2].heroId
        compose.onNodeWithTag("contract_up_${id.value}").performScrollTo().assertIsDisplayed()
        val bounds = compose.onNodeWithTag("contract_up_${id.value}").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height >= 48 * context.resources.displayMetrics.density - 1)
        shot("contract_sheet", "party")
        assertEquals(run, (vm.ui.value as UiState.Playing).state)
        back()
        compose.onNodeWithTag("contract_sheet").assertDoesNotExist()
        assertEquals(run.gold, (vm.ui.value as UiState.Playing).state.gold)
    }

    @Test
    fun theShopKeepsItsPlaceAcrossTheForgeAndTheMenu() {
        play(stocked())
        compose.onNodeWithTag("end_day").assertIsDisplayed()
        compose.onNodeWithTag("shop_list").performScrollToNode(hasTestTag("shop_storage"))
        compose.onNodeWithTag("shop_storage").assertIsDisplayed()
        compose.onNodeWithTag("shop_counter").assertDoesNotExist()   // scrolled away: the list is longer than the screen

        compose.onNodeWithTag("nav_forge").performClick()
        // Arriving at the Forge moves nothing: the workbench at its top and its one action are in view, and End Day is the Shop's.
        compose.onNodeWithTag("forge_workbench").assertIsDisplayed()
        compose.onNodeWithTag("forge_weapon").assertIsDisplayed()
        compose.onNodeWithTag("end_day").assertDoesNotExist()
        back()
        compose.onNodeWithTag("shop_storage").assertIsDisplayed()
        compose.onNodeWithTag("shop_counter").assertDoesNotExist()

        // Back on the Shop opens the main menu; "Continue run" is the Shop where it was left.
        back()
        compose.onNodeWithTag("main_menu").assertIsDisplayed()
        compose.onNodeWithTag("menu_continue").performClick()
        compose.onNodeWithTag("shop_storage").assertIsDisplayed()
        compose.onNodeWithTag("shop_counter").assertDoesNotExist()
    }

    @Test
    fun theForgeKeepsItsDraftAndItsOpenStepAndEndDayStaysOffTownAndRecords() {
        val vm = play(stocked())
        compose.onNodeWithTag("nav_forge").performClick()
        compose.onNodeWithTag("forge_option_sword").performClick()
        // The tray has moved on to the metal: the player opens the weapon's place again.
        compose.onNodeWithTag("forge_slot_weapon").performClick()
        compose.onNodeWithTag("forge_tray_weapon").assertIsDisplayed()
        val draft = (vm.ui.value as UiState.Playing).draft

        compose.onNodeWithTag("nav_town").performClick()
        compose.onNodeWithTag("town_list").assertIsDisplayed()
        compose.onNodeWithTag("end_day").assertDoesNotExist()
        compose.onNodeWithTag("nav_records").performClick()
        compose.onNodeWithTag("page_legacy").performClick()
        compose.onNodeWithTag("end_day").assertDoesNotExist()

        compose.onNodeWithTag("nav_forge").performClick()
        assertEquals(draft, (vm.ui.value as UiState.Playing).draft)
        compose.onNode(hasContentDescription("Weapon: Sword", substring = true)).assertExists()
        // The place the player opened is still open, and still where it was on screen.
        compose.onNodeWithTag("forge_tray_weapon").assertIsDisplayed()
        compose.onNodeWithTag("nav_records").performClick()
        compose.onNodeWithTag("page_legacy").assertIsSelected()
    }

    @Test
    fun aBladeOpensInsideStorageAndComesBackToTheSameList() {
        val vm = play(stocked())
        compose.onNodeWithTag("shop_list").performScrollToNode(hasTestTag("shop_storage"))
        compose.onNodeWithTag("shop_storage").performClick()
        compose.onNodeWithText("Weakest").performClick()
        compose.onNodeWithTag("stock_t2").performClick()

        // One sheet: the blade stands in the Storage sheet, under the way back.
        compose.onNodeWithTag("item_sheet").assertDoesNotExist()
        compose.onNodeWithTag("sheet_back").assertIsDisplayed()
        compose.onNodeWithTag("storage_list").assertDoesNotExist()
        back()
        compose.onNodeWithTag("storage_list").assertIsDisplayed()
        compose.onNodeWithText("Weakest").assertIsSelected()
        assertEquals(null, (vm.ui.value as UiState.Playing).sheet)

        // Listed from its sheet: back on the list, which no longer has it, and the sheet says where it went.
        compose.onNodeWithTag("stock_t2").performClick()
        compose.onNodeWithTag("item_list").performScrollTo().performClick()
        compose.waitUntil(15_000) { (vm.ui.value as UiState.Playing).let { it.sheet == null && !it.busy } }
        compose.onNodeWithTag("storage_list").assertIsDisplayed()
        compose.onNode(hasTestTag("stock_t2") and hasAnyAncestor(hasTestTag("storage_list"))).assertDoesNotExist()   // it is on the Shop's shelf now
        compose.onNodeWithText("Listed Blade 2 for", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Weakest").assertIsSelected()

        // Melted down from its sheet: the same, on purpose.
        compose.onNodeWithTag("stock_t1").performClick()
        compose.onNodeWithTag("item_salvage").performScrollTo().performClick()
        compose.waitUntil(15_000) { (vm.ui.value as UiState.Playing).let { it.sheet == null && !it.busy } }
        compose.onNodeWithTag("storage_list").assertIsDisplayed()
        compose.onNodeWithTag("stock_t1").assertDoesNotExist()
        compose.onNodeWithText("Salvaged Blade 1.", substring = true).assertIsDisplayed()

        // Closing Storage closes the whole flow.
        back()
        compose.onNodeWithTag("storage_sheet").assertDoesNotExist()
        compose.onNodeWithTag("shop_list").assertIsDisplayed()
    }

    @Test
    fun backOnTheFirstCardOfADayOpensTheMenuAndTheDayIsStillThere() {
        val vm = play(engine.newRun(LegacyProfile(), 42L))
        compose.onNodeWithTag("end_day").performClick()
        compose.waitUntil(15_000) { vm.ui.value is UiState.ShopDay }
        compose.onNodeWithTag("shopday_next").performClick()
        compose.waitForIdle()
        assertEquals(1, (vm.ui.value as UiState.ShopDay).position.at)
        val recorded = (vm.ui.value as UiState.ShopDay).state.lastResolution

        back()   // the card before
        assertEquals(0, (vm.ui.value as UiState.ShopDay).position.at)
        back()   // the first card: the menu
        compose.onNodeWithTag("main_menu").assertIsDisplayed()
        val day = vm.ui.value as UiState.ShopDay
        assertEquals(0, day.position.at)
        assertEquals(recorded, day.state.lastResolution)
        assertTrue("not acknowledged", runBlocking { store.load() }.cursor?.let { DayCursor.decode(it) }?.stage != DayCursor.Stage.DONE)

        compose.onNodeWithTag("menu_continue").performClick()
        compose.onNodeWithTag("shopday_progress").assertIsDisplayed()
        compose.onNodeWithTag("shopday_next").assertIsDisplayed()
        compose.onNodeWithTag("shop_list").assertIsNotDisplayed()
    }

    private companion object {
        /** Never "tiny_blacksmith.db": a test run must not touch a save on the device. */
        const val DB = "navigation_test.db"
    }
}
