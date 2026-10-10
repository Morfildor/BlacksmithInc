package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.shopday.toUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * What the screens are handed is built on the ViewModel's compute dispatcher and nowhere else (E03, X12): while that
 * dispatcher is held nothing new reaches the screen, and each model is built once per saved state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainThreadTest : ShopDayTestBase() {

    /** A dispatcher that runs nothing until told to, and counts what it ran. */
    private class Held : CoroutineDispatcher() {
        val queue = ArrayDeque<Runnable>()
        var ran = 0
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue += block }
        fun runAll() { while (queue.isNotEmpty()) { queue.removeFirst().run(); ran++ } }
    }

    private suspend fun TestScope.release(held: Held) { advanceUntilIdle(); held.runAll(); advanceUntilIdle() }

    @Test
    fun theShopDayIsLaidOutOnComputeOncePerSavedState() = vmTest {
        val held = Held()
        val vm = GameViewModel(engine, session(repo(stocked.nextDay())), FakeSettings(), SavedStateHandle(), compute = held)
        advanceUntilIdle()
        assertEquals("the day is not shown before it has been laid out off the main thread", UiState.Loading, vm.ui.value)
        assertEquals(1, held.queue.size)
        release(held)

        val day = vm.day()
        assertEquals(day.script.toUi(day.state, engine.content, engine.config), day.model)
        assertEquals(1, held.ran)
        // Moving through the day builds nothing: the same model object, and nothing more asked of compute.
        vm.resumeDay(); advanceUntilIdle()
        repeat(3) { vm.next(); advanceUntilIdle() }
        assertTrue(vm.day().position.at > 0)
        assertSame(day.model, vm.day().model)
        vm.openSheet(Sheet.Hero(day.state.heroes.keys.first())); advanceUntilIdle()
        vm.closeSheet(); advanceUntilIdle()
        assertSame(day.model, vm.day().model)
        assertEquals("nothing was queued", 0, held.queue.size)
        assertEquals(1, held.ran)
    }

    @Test
    fun theShopAndTheSiegeForecastAreBuiltOnComputeOncePerSavedState() = vmTest {
        val held = Held()
        val vm = GameViewModel(engine, session(repo(fresh)), FakeSettings(), SavedStateHandle(), compute = held)
        advanceUntilIdle()
        assertEquals("planning is not shown before its content has been built off the main thread", UiState.Loading, vm.ui.value)
        release(held)

        val first = vm.playing()
        assertEquals(engine.shopUi(fresh), first.shop)
        assertNotNull("a new run already has a besieger to weigh", first.forecast)
        assertEquals(engine.siegeForecast(fresh), first.forecast)
        assertEquals(1, held.ran)

        // Walking the destinations, opening a sheet, editing the draft: the same objects, nothing built.
        Dest.entries.forEach { vm.selectDest(it); advanceUntilIdle() }
        vm.openSheet(Sheet.Hero(fresh.heroes.keys.first())); advanceUntilIdle()
        vm.updateDraft { it.copy(risk = com.tinyblacksmith.core.model.Risk.SAFE) }; advanceUntilIdle()
        assertSame(first.shop, vm.playing().shop)
        assertSame(first.forecast, vm.playing().forecast)
        assertEquals(1, held.ran)

        // An accepted command is a new saved state: the screen keeps the old content until the new one is built on compute.
        vm.dispatch(forgeSword); advanceUntilIdle()
        assertSame("still the last built state", first.state, vm.playing().state)
        release(held)
        val second = vm.playing()
        assertEquals(1, second.state.storedWeapons().size)
        assertEquals(engine.shopUi(second.state), second.shop)
        assertEquals(engine.siegeForecast(second.state), second.forecast)
        assertEquals(2, held.ran)
    }
}
