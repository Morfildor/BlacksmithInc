package com.example.blacksmithproject

import com.example.blacksmithproject.ui.BulkTerms
import com.example.blacksmithproject.ui.StorageFilter
import com.example.blacksmithproject.ui.StorageSort
import com.example.blacksmithproject.ui.detail.StockAction
import com.example.blacksmithproject.ui.detail.toCommand
import com.example.blacksmithproject.ui.salvageTerms
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.shown
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Storage's filters, orders and bulk actions: the list is narrowed from the rows the Shop was given, and a bulk action is existing commands, one per blade. */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageToolsTest : ShopDayTestBase() {

    /** A storeroom of [n] copies of a real forged blade, varied by hand in family, rarity, power and past owner. */
    private fun storeroom(n: Int) = stocked.listedWeapons().first().let { blade ->
        val families = listOf(LaunchContent.SWORD, LaunchContent.BOW, LaunchContent.AXE)
        stocked.copy(weapons = (1..n).associate { i ->
            WeaponId("t$i") to blade.copy(
                id = WeaponId("t$i"), name = "Blade $i", location = WeaponLocation.Storage, familyId = families[i % 3], power = 10 + (i * 7) % 23,
                rarity = Rarity.entries[i % 3], ownerIds = if (i % 4 == 0) listOf(HeroId("h")) else emptyList(), legendKey = "old".takeIf { i % 10 == 0 },
            )
        })
    }

    @Test
    fun filtersNarrowByFamilyRarityAndNeverSoldAndOrdersKeepTheStoredOrderAmongEquals() {
        val state = storeroom(3000)
        val storage = engine.shopUi(state).storage
        assertEquals(3000, storage.size)
        assertEquals("no filter is the stored list itself", storage, storage.shown(StorageFilter()))

        val bows = storage.shown(StorageFilter(family = engine.content.family(LaunchContent.BOW).name))
        assertEquals(state.storedWeapons().filter { it.familyId == LaunchContent.BOW }.map { it.id }, bows.map { it.weapon.id })
        val rare = storage.shown(StorageFilter(rarity = Rarity.RARE))
        assertEquals(state.storedWeapons().filter { it.rarity == Rarity.RARE }.map { it.id }, rare.map { it.weapon.id })
        // Never sold: no hero has held it and it did not come back from the Legend Board.
        val unsold = storage.shown(StorageFilter(unsold = true))
        assertEquals(state.storedWeapons().filter { it.ownerIds.isEmpty() && it.legendKey == null }.map { it.id }, unsold.map { it.weapon.id })
        assertTrue(unsold.isNotEmpty() && unsold.size < storage.size)
        val all = storage.shown(StorageFilter(engine.content.family(LaunchContent.BOW).name, Rarity.RARE, unsold = true))
        assertEquals(bows.map { it.weapon.id }.toSet() intersect rare.map { it.weapon.id }.toSet() intersect unsold.map { it.weapon.id }.toSet(), all.map { it.weapon.id }.toSet())

        val weakest = storage.shown(StorageFilter(sort = StorageSort.WEAKEST))
        assertEquals(storage.sortedBy { it.weapon.power }.map { it.weapon.id }, weakest.map { it.weapon.id })
        assertEquals("equal blades stay in stored order", storage.filter { it.weapon.power == weakest.first().weapon.power }.map { it.weapon.id }, weakest.takeWhile { it.weapon.power == weakest.first().weapon.power }.map { it.weapon.id })
        assertEquals(storage.maxOf { it.weapon.power }, storage.shown(StorageFilter(sort = StorageSort.STRONGEST)).first().weapon.power)
        assertEquals(storage.maxOf { it.suggested }, storage.shown(StorageFilter(sort = StorageSort.DEAREST)).first().suggested)
    }

    @Test
    fun aRowSaysItsFamilyAndWhetherAHeroEverCarriedIt() = vmTest {
        val vm = open(repo(fresh))
        vm.dispatch(forgeSword); advanceUntilIdle()
        val row = vm.playing().shop.storage.single()
        assertEquals(engine.content.family(LaunchContent.SWORD).name, row.family)
        assertTrue("a blade just forged was never sold", row.unsold)
    }

    /** Five blades chosen with no energy left: four go on overwork, the fifth is refused, said, and still in storage. One save per blade. */
    @Test
    fun aBulkSalvageIsOneCommandPerBladeAndStopsAtTheFirstRefusal() = vmTest {
        val store = repo(fresh)
        val vm = open(store)
        repeat(5) {
            vm.dispatch(Command.BuyMaterial(LaunchContent.IRON)); advanceUntilIdle()
            vm.dispatch(Command.BuyMaterial(LaunchContent.EMBER_RESIN)); advanceUntilIdle()
            vm.dispatch(forgeSword); advanceUntilIdle()
            vm.dismissReveal(); vm.dismissError()
        }
        val before = vm.playing()
        val ids = before.shop.storage.map { it.weapon.id }
        assertEquals(5, ids.size)
        val room = engine.config.maxOverworkPerDay - before.state.overworkToday + before.state.energy / engine.config.salvageEnergy
        assertTrue("the test needs more blades than the day can salvage: room for $room", room in 1..4)
        val commits = store.commitCount
        val iron = before.state.materials[LaunchContent.IRON] ?: 0

        vm.dispatchAll(ids.map { StockAction.Salvage.toCommand(it) }); advanceUntilIdle()

        val after = vm.playing()
        assertEquals("one save per salvaged blade", room, store.commitCount - commits)
        assertEquals("the blades are taken in the order given", ids.drop(room), after.shop.storage.map { it.weapon.id })
        assertEquals(iron + room, after.state.materials[LaunchContent.IRON])
        assertNotNull("the refusal is said", after.lastError)
        assertTrue(after.lastError!!, after.lastError!!.startsWith("Not enough energy"))
        assertFalse(after.busy)
        // What is on screen is what is stored.
        assertEquals(after.state, com.tinyblacksmith.core.persistence.SaveCodec.decodeRun(store.run!!))
    }

    @Test
    fun aBulkGiftToTheWatchStopsWhenTheArmoryIsFull() = vmTest {
        val state = storeroom(40)
        val store = planning(state)
        val vm = open(store)
        val ids = vm.playing().shop.storage.map { it.weapon.id }
        vm.dispatchAll(ids.map { StockAction.Donate.toCommand(it) }); advanceUntilIdle()
        val after = vm.playing()
        assertEquals(engine.config.armoryMax, after.state.town.armory)
        assertEquals("The town watch armory is full.", after.lastError)
        assertTrue("some were given and the rest are still stored", after.shop.storage.size in 1 until 40)
        assertEquals(ids.takeLast(after.shop.storage.size), after.shop.storage.map { it.weapon.id })
    }

    @Test
    fun theSalvageConfirmationSaysTheCostAndWarnsOfOverworkOnlyWhenItIsReached() {
        val terms = BulkTerms(salvageEnergy = 1, energy = 3, overworkLeft = 4, armoryRoom = 30)
        assertTrue(salvageTerms(3, terms).endsWith("That is 3 energy; you have 3."))
        val over = salvageTerms(12, terms)
        assertTrue(over, "That is 12 energy; you have 3." in over && "overwork (4 left today)" in over && "stops" in over)
    }
}
