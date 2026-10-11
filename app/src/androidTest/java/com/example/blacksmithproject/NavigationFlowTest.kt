package com.example.blacksmithproject

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
