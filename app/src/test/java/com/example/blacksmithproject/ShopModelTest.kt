package com.example.blacksmithproject

import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.Tips
import com.example.blacksmithproject.ui.leadActionLabel
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.shopday.Demand
import com.tinyblacksmith.core.shopday.LeadKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import com.tinyblacksmith.core.model.HistoryEntry
import com.tinyblacksmith.core.model.WeaponLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Shop destination's content as the ViewModel hands it to the screen: built from the saved state, and only from it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShopModelTest : ShopDayTestBase() {

    /** The counter's plate says the shelf first, in whole words; what the siege brings is the strip's, not the plate's. */
    @Test
    fun theCounterPlateIsTheShelfAndTheDaysCustomers() {
        val shop = engine.shopUi(stocked)
        assertEquals("Shelf ${shop.shelf.size} of ${shop.slots} · ${shop.seats} customers a day", shop.plate)
        assertTrue(shop.shelf.isNotEmpty())
    }

    /** "Resists Grave · Grave danger" read as one fact: no outlook is worded with an element's name. */
    @Test
    fun noOutlookBorrowsAnElementsName() {
        for (odds in com.tinyblacksmith.core.battle.Battle.SiegeOdds.entries) {
            val words = com.example.blacksmithproject.ui.Labels.outlook(odds).lowercase().split(' ')
            for (e in com.tinyblacksmith.core.content.Element.entries) assertTrue("$odds / $e", e.name.lowercase() !in words)
        }
    }

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

    /** A shelf row and the blade's sheet read one function: a number on the row is the number on the card. */
    @Test
    fun aStockRowSaysTheNumbersOfTheItemCard() {
        val blade = stocked.listedWeapons().first()
        val marked = blade.copy(affixes = listOf(engine.content.affixes.first { it.kind == AffixKind.BENEFICIAL }.id), flaws = listOf(engine.content.affixes.first { it.kind == AffixKind.FLAW }.id), condition = 35)
        val state = stocked.copy(weapons = stocked.weapons + (blade.id to marked))
        val row = engine.shopUi(state).shelf.first { it.weapon.id == blade.id }
        val card = engine.itemDetail(state, blade.id)!!
        assertEquals(card.stats.filter { it.max != null || it.value > 0 }, row.stats)
        assertEquals(listOf(marked.power, marked.quality, 35), row.stats.map { it.value })   // an unsung blade's renown of 0 is left out
        assertEquals(card.affixes.map { it.name }, row.buffs)
        assertEquals(card.flaws.map { it.name }, row.flaws)
    }

    /** A new game (A01): the Shop opens on the first-blade lead with its reason, and the lead's button goes to the forge. */
    @Test
    fun dayOneLeadsWithForgeYourFirstBlade() = vmTest {
        val vm = open(repo(fresh))
        val lead = vm.playing().shop.lead
        assertEquals(LeadKind.FIRST_BLADE, lead.kind)
        assertEquals("Forge your first weapon" to "${fresh.aliveHeroes().size} heroes in town. Your shelf is empty.", lead.action to lead.reason)
        assertEquals("Go to the forge", leadActionLabel(lead.kind))
        assertEquals("a new game opens on the Shop", Dest.SHOP, vm.playing().dest)
        // The coach line of the first counter visit is a tip like the others: unseen on a new install, and never a banner of a destination.
        assertTrue(Tips.COUNTER.id in Tips.ALL && Dest.entries.none { Tips.COUNTER in Tips.forDest(it) })
    }

    @Test
    fun theShopFollowsEveryAcceptedCommand() = vmTest {
        val vm = open(repo(fresh))
        assertEquals("Forge your first weapon", vm.playing().shop.lead.action)
        assertNull("day 1 has no yesterday", vm.playing().shop.yesterday)
        vm.dispatch(forgeSword); advanceUntilIdle()
        val blade = vm.playing().state.storedWeapons().single()
        assertEquals(listOf(blade.id), vm.playing().shop.storage.map { it.weapon.id })
        vm.dispatch(Command.ToggleShelf(blade.id, true, 40)); advanceUntilIdle()
        val shop = vm.playing().shop
        assertEquals(listOf(blade.id to 40), shop.shelf.map { it.weapon.id to it.price })
        assertTrue(shop.storage.isEmpty())
        // The same object the screen is given is what the engine would build for the saved state.
        assertEquals(vm.playing().state.let { engine.shopUi(it, engine.siegeForecast(it)) }, shop)
        assertEquals(Demand.summary(vm.playing().state, engine.content, engine.config).living.toString(), shop.demand.first().value)
        assertEquals(engine.config.customers.shopCapacity, shop.seats)
    }

    /**
     * A blade traded in at yesterday's counter is on the Shop's "Back at the forge" card the next morning, with the line it
     * came back by; on the shelf, a day later or in another era it is not. Played on the real engine, then varied.
     */
    @Test
    fun aTradeInIsBackAtTheForgeForOneMorning() {
        val day = (42L..53L).asSequence().flatMap { ShopDayFixtures.run(it, 40) }.firstOrNull { d ->
            d.resolution.visits.any { v -> v.sale?.tradeInWeaponId?.let { d.state.weapons[it]?.isInStorage } == true }
        }
        assertNotNull("no morning after a trade-in that is still in storage", day)
        val state = day!!.state
        val id = day.resolution.visits.firstNotNullOf { v -> v.sale?.tradeInWeaponId?.takeIf { state.weapons[it]?.isInStorage == true } }
        val back = engine.shopUi(state).returned.single { it.stock.weapon.id == id }
        assertEquals(state.day - 1, back.day)
        assertTrue(back.how, back.how.startsWith("Traded in by"))
        assertEquals(engine.suggestedPrice(state.weapons[id]!!), back.stock.suggested)
        assertTrue(back.salvage, back.salvage.startsWith("Salvage (${engine.config.salvageEnergy} energy"))
        // Every blade on the card is in storage, and each is there once.
        assertTrue(engine.shopUi(state).returned.all { r -> state.weapons[r.stock.weapon.id]!!.isInStorage })
        val w = state.weapons[id]!!
        fun returnedIn(s: com.tinyblacksmith.core.model.GameState) = engine.shopUi(s).returned.any { it.stock.weapon.id == id }
        assertFalse("listed, it leaves the card", returnedIn(state.copy(weapons = state.weapons + (id to w.copy(location = WeaponLocation.Shelf(w.power))))))
        assertFalse("a morning later it is ordinary stock", returnedIn(state.copy(day = state.day + 1)))
        assertFalse("another era's return is not this one's", returnedIn(state.copy(weapons = state.weapons + (id to w.copy(history = w.history.map { it.copy(era = it.era + 1) })))))
    }

    /** Each way back to the forge is on the card: a fallen hero's blade recovered, a loan handed back, a legend come home. A forged blade is not. */
    @Test
    fun everyWayBackIsOnTheCard() {
        val blade = stocked.listedWeapons().first()
        for (kind in listOf("RECOVERED", "LOAN_RETURNED", "RETURNED")) {
            val came = blade.copy(location = WeaponLocation.Storage, history = blade.history + HistoryEntry(stocked.era, stocked.day, kind, "Came back by $kind."))
            val shop = engine.shopUi(stocked.copy(weapons = stocked.weapons + (blade.id to came)))
            assertEquals(kind, "Came back by $kind.", shop.returned.single().how)
        }
        assertTrue("a blade only forged and stored is not back from anyone", engine.shopUi(stocked.copy(weapons = stocked.weapons + (blade.id to blade.copy(location = WeaponLocation.Storage)))).returned.isEmpty())
    }
}
