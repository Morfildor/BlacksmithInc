package com.example.blacksmithproject

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole app over a real Room file (its own, never the game's save): what an activity recreation, a restored
 * instance state and a new process each bring back. The JVM twin over a fake store is `LifecycleTest`.
 */
@RunWith(AndroidJUnit4::class)
class ShopDayPersistenceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val engine = GameEngine()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clean() { context.deleteDatabase(DB) }

    @After
    fun cleanUp() { context.deleteDatabase(DB) }

    /** A store holding [run] on planning: its last day, if any, watched to the end. */
    private fun seed(run: GameState): SaveStore = SaveStore.create(context, DB).also { store ->
        runBlocking {
            store.commit(SaveCodec.encodeRun(run), SaveCodec.encodeLegacy(run.legacy))
            run.lastResolution?.let { store.saveCursor(DayCursor(it.commandId.value, DayCursor.Stage.DONE).encode()) }
        }
    }

    private fun viewModelOn(store: SaveStore): GameViewModel {
        lateinit var vm: GameViewModel
        compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, store), QuietSettings(), SavedStateHandle()) }
        return vm
    }

    @Test
    fun realRoomSurvivesRecreation() {
        val store = seed(ShopDayFixtures.run(42, 2).last().state)
        val factory = viewModelFactory { initializer { GameViewModel(engine, GameSession(engine, store), QuietSettings(), createSavedStateHandle()) } }
        // The activity owns the ViewModel, as MainActivity does, and sets the app as its content.
        fun show(): GameViewModel {
            lateinit var vm: GameViewModel
            compose.activityRule.scenario.onActivity { activity ->
                vm = ViewModelProvider(activity, factory)[GameViewModel::class.java]
                activity.setContent { BlacksmithProjectTheme { TinyBlacksmithApp(vm) } }
            }
            return vm
        }

        val vm = show()
        compose.waitUntil(15_000) { vm.ui.value is UiState.Playing }
        compose.onNodeWithTag("menu_continue").performClick()
        compose.onNodeWithTag("end_day").performClick()
        compose.waitUntil(15_000) { vm.ui.value is UiState.ShopDay }
        compose.onNodeWithTag("shopday_next").performClick()
        compose.waitForIdle()
        val day = vm.ui.value as UiState.ShopDay
        assertEquals(1, day.position.at)
        val progress = day.model.beats[1].progress
        compose.onNodeWithTag("shopday_progress").assertTextEquals(progress)
        val id = day.state.lastResolution!!.commandId.value
        // The card reached is written behind the screen; the day itself was committed before it was shown.
        compose.waitUntil(15_000) { runBlocking { store.load() }.cursor?.let { DayCursor.decode(it) } == day.position.cursor(id) }
        val rows = runBlocking { store.load() }
        assertEquals("the stored run is the day on screen", SaveCodec.encodeRun(day.state), rows.run)

        // Recreation (a configuration change): the same ViewModel, the same card, no menu, and not a byte written.
        compose.activityRule.scenario.recreate()
        val recreated = show()
        compose.waitForIdle()
        assertSame(vm, recreated)
        compose.onNodeWithTag("main_menu").assertDoesNotExist()
        compose.onNodeWithTag("shopday_progress").assertTextEquals(progress)
        assertEquals(day.position.at, (recreated.ui.value as UiState.ShopDay).position.at)
        assertEquals(rows, runBlocking { store.load() })

        // A new process: only the file. The day is read back, not simulated again, and opens on the card that was reached.
        val reopened = SaveStore.create(context, DB)
        val cold = viewModelOn(reopened)
        compose.waitUntil(15_000) { cold.ui.value is UiState.ShopDay }
        val resumed = cold.ui.value as UiState.ShopDay
        assertTrue("opened onto an unwatched day: the Resume prompt", resumed.resumed)
        assertEquals(day.position.at, resumed.position.at)
        assertEquals(day.state, resumed.state)
        assertEquals(day.model, resumed.model)
        assertEquals(rows, runBlocking { reopened.load() })
    }

    @Test
    fun theMenuStaysClosedAfterSavedStateRestoration() {
        val vm = viewModelOn(seed(engine.newRun(LegacyProfile(), 42L)))
        val tester = StateRestorationTester(compose)
        tester.setContent { BlacksmithProjectTheme { TinyBlacksmithApp(vm) } }
        compose.waitUntil(15_000) { vm.ui.value is UiState.Playing }
        compose.onNodeWithTag("menu_continue").performClick()
        compose.onNodeWithTag("shop_list").assertIsDisplayed()

        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("main_menu").assertDoesNotExist()
        compose.onNodeWithTag("shop_lead", useUnmergedTree = true).assertTextEquals("Forge your first weapon")
    }

    @Test
    fun anOpenStorageSheetAndItsOrderComeBackAfterSavedStateRestoration() {
        val played = ShopDayFixtures.run(42, 2).last().state
        val blade = played.weapons.values.first()
        val stored = played.copy(weapons = played.weapons + (1..3).associate { i -> WeaponId("t$i").let { it to blade.copy(id = it, name = "Blade $i", location = WeaponLocation.Storage, power = blade.power + i) } })
        val vm = viewModelOn(seed(stored))
        val tester = StateRestorationTester(compose)
        tester.setContent { BlacksmithProjectTheme { TinyBlacksmithApp(vm) } }
        compose.waitUntil(15_000) { vm.ui.value is UiState.Playing }
        compose.onNodeWithTag("menu_continue").performClick()
        compose.onNodeWithTag("shop_list").performScrollToNode(hasTestTag("shop_storage"))
        compose.onNodeWithTag("shop_storage").performClick()
        compose.onNodeWithTag("storage_list").assertIsDisplayed()
        compose.onNodeWithText("Weakest").performClick()
        compose.onNodeWithText("Weakest").assertIsSelected()

        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("main_menu").assertDoesNotExist()
        compose.onNodeWithTag("storage_list").assertIsDisplayed()
        compose.onNodeWithText("Weakest").assertIsSelected()
    }

    private companion object {
        /** Never "tiny_blacksmith.db": a test run must not touch a save on the device. */
        const val DB = "lifecycle_test.db"
    }
}
