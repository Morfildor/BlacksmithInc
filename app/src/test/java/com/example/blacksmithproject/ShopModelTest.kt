package com.example.blacksmithproject

import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.shopday.Demand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Shop destination's content as the ViewModel hands it to the screen: built from the saved state, and only from it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShopModelTest : ShopDayTestBase() {

    /** End Day, skip to the Tomorrow card, begin the next day: the Shop opens on the lead that card showed. Five days running. */
    @Test
    fun theShopOpensOnTheLeadTheTomorrowCardShowed() = vmTest {
        val vm = open(repo(stocked))
        repeat(5) {
            vm.endDay(); advanceUntilIdle()
            vm.skipDay(); advanceUntilIdle()
            if (vm.day().position.beat == com.example.blacksmithproject.Beat.Blessing) { vm.decideLater(); advanceUntilIdle() }
            val day = vm.day()
            val card = day.script.toUi(day.state, engine.content, engine.config).beats.last() as Beat.Tomorrow
            vm.acknowledge(); advanceUntilIdle()
            val shop = vm.playing().shop
            assertEquals("day ${day.state.day}", card.action to card.reason, shop.lead.action to shop.lead.reason)
            assertEquals("yesterday is the day just watched", day.state.day - 1, shop.yesterday?.day)
        }
    }

    @Test
    fun theShopFollowsEveryAcceptedCommand() = vmTest {
        val vm = open(repo(fresh))
        assertEquals("Forge your first blade", vm.playing().shop.lead.action)
        assertNull("day 1 has no yesterday", vm.playing().shop.yesterday)
        vm.dispatch(forgeSword); advanceUntilIdle()
        val blade = vm.playing().state.storedWeapons().single()
        assertEquals(listOf(blade.id), vm.playing().shop.storage.map { it.weapon.id })
        vm.dispatch(Command.ToggleShelf(blade.id, true, 40)); advanceUntilIdle()
        val shop = vm.playing().shop
        assertEquals(listOf(blade.id to 40), shop.shelf.map { it.weapon.id to it.price })
        assertTrue(shop.storage.isEmpty())
        // The same object the screen is given is what the engine would build for the saved state.
        assertEquals(engine.shopUi(vm.playing().state), shop)
        assertEquals(Demand.summary(vm.playing().state, engine.content, engine.config).living.toString(), shop.demand.first().value)
        assertEquals(engine.config.customers.shopCapacity, shop.seats)
    }
}
