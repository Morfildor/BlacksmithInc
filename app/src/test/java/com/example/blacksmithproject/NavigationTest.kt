package com.example.blacksmithproject

import com.example.blacksmithproject.ui.detail.StockAction
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.persistence.DayCursor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moving about the workshop: what a destination change keeps, where Back leads, and what a stock change leaves on screen.
 * Scroll positions and the open forge step are the screen's own state and are tested on a device (`NavigationFlowTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationTest : ShopDayTestBase() {
    @Test
    fun aDestinationChangeKeepsTheDraftAndOnlyForgeThisChangesIt() = vmTest {
        val vm = open(repo(fresh))
        vm.updateDraft { it.copy(familyId = LaunchContent.SWORD, coreId = LaunchContent.IRON) }; advanceUntilIdle()
        val draft = vm.playing().draft
        for (dest in listOf(Dest.TOWN, Dest.RECORDS, Dest.SHOP, Dest.FORGE)) { vm.selectDest(dest); advanceUntilIdle() }
        vm.selectRecords(RecordsPage.JOURNAL); vm.selectDest(Dest.FORGE); advanceUntilIdle()
        assertEquals("the recipe is as it was left", draft, vm.playing().draft)
        assertEquals("nothing asked the forge to show a step", 0, vm.playing().forgeReveal)
        assertEquals("Records is on the segment it was left on", RecordsPage.JOURNAL, vm.playing().records)

        // "Forge this" is the one deliberate change: it sets the family, keeps the rest and asks the Forge to show the next step.
        vm.selectDest(Dest.SHOP); vm.forgeFamily(LaunchContent.SPEAR); advanceUntilIdle()
        val s = vm.playing()
        assertEquals(Dest.FORGE, s.dest)
        assertEquals(draft.copy(familyId = LaunchContent.SPEAR), s.draft)
        assertEquals(1, s.forgeReveal)
    }

    @Test
    fun backClosesTheSheetThenReturnsToShopAndIsThenLeftToTheMenu() = vmTest {
        val vm = open(repo(stocked))
        val blade = vm.playing().shop.shelf.first().weapon.id
        vm.selectDest(Dest.TOWN); vm.openSheet(Sheet.Item(blade)); advanceUntilIdle()

        assertTrue(vm.back()); advanceUntilIdle()
        assertEquals("the sheet closes first, the destination stays", null to Dest.TOWN, vm.playing().sheet to vm.playing().dest)
        assertTrue(vm.back()); advanceUntilIdle()
        assertEquals(Dest.SHOP, vm.playing().dest)
        // Not consumed on Shop: the screen opens the main menu, and nothing of the run has changed.
        val before = vm.playing()
        assertFalse(vm.back()); advanceUntilIdle()
        assertEquals(before, vm.playing())
    }

    @Test
    fun aBulkActionSaysHowManyBladesItTook() = vmTest {
        val vm = open(repo(fresh))
        vm.dispatch(forgeSword); advanceUntilIdle()
        val id = vm.playing().revealWeaponId!!
        vm.storeForged(); advanceUntilIdle()
        vm.dispatchAll(listOf(Command.Salvage(id))); advanceUntilIdle()
        assertEquals("1 blade melted down.", vm.playing().notice)
        // Nothing done, nothing claimed: the blade is gone, so the engine refuses and only the reason shows.
        vm.dismissNotice("1 blade melted down."); vm.dispatchAll(listOf(Command.Salvage(id))); advanceUntilIdle()
        assertNull(vm.playing().notice)
    }

    @Test
    fun puttingOffABlessingKeepsTheDestination() = vmTest {
        val vm = open(repo(fresh))
        vm.selectDest(Dest.FORGE); vm.dismissBlessingOffer(); advanceUntilIdle()
        assertEquals(Dest.FORGE, vm.playing().dest)
        assertEquals(vm.playing().state.day, vm.playing().blessingOfferDismissedDay)
    }

    @Test
    fun backOnTheResumePromptAndTheFirstCardLeavesTheDayUnwatchedWhereItWas() = vmTest {
        val repo = repo(stocked)
        val vm = open(repo)
        vm.endDay(); advanceUntilIdle()
        val report = vm.day().state.lastResolution!!
        // The first card: Back is left to the menu, and the day is neither moved nor acknowledged.
        assertFalse(vm.back()); advanceUntilIdle()
        assertEquals(0, vm.day().position.at)
        vm.next(); advanceUntilIdle(); vm.next(); advanceUntilIdle()
        assertEquals(2, vm.day().position.at)

        // The game is opened again onto that day: the Resume prompt. Back there is left to the menu too.
        val again = open(repo)
        assertTrue(again.day().resumed)
        assertFalse(again.back()); advanceUntilIdle()
        val day = again.day()
        assertTrue("still the Resume prompt", day.resumed)
        assertEquals("at the card it was left on", 2, day.position.at)
        assertEquals("the recorded day is untouched", report, day.state.lastResolution)
        assertTrue("and not acknowledged", repo.storedCursor()?.stage != DayCursor.Stage.DONE)
        // "Continue run" then "Resume the day": the same card.
        again.resumeDay(); advanceUntilIdle()
        assertEquals(false to 2, again.day().resumed to again.day().position.at)
    }

    @Test
    fun aStockChangeSaysWhatHappenedAndClosesTheSheetOnlyWhenTheBladeLeft() = vmTest {
        val vm = open(repo(fresh))
        vm.dispatch(Command.BuyMaterial(LaunchContent.IRON)); advanceUntilIdle()
        vm.dispatch(forgeSword); advanceUntilIdle()
        val id = vm.playing().revealWeaponId!!
        vm.storeForged(); advanceUntilIdle()
        val name = vm.playing().state.weapons.getValue(id).name
        val slots = engine.shelfSlots(vm.playing().state)

        // Opening a sheet puts the older notice away: the sheet says only what is done in it.
        vm.openSheet(Sheet.Item(id)); advanceUntilIdle()
        assertNull(vm.playing().notice)

        // Listed: the blade has left storage, so its sheet closes (onto the Storage list it was opened from).
        vm.stock(id, StockAction.ListAt(50)); advanceUntilIdle()
        assertEquals(null to "$name is on the shelf at 50 gold. Shelf 1 of $slots.", vm.playing().sheet to vm.playing().notice)

        // A new price, an unlisting and a hone keep the sheet on the blade.
        vm.openSheet(Sheet.Item(id)); advanceUntilIdle()
        vm.stock(id, StockAction.SetPrice(40)); advanceUntilIdle()
        assertEquals(Sheet.Item(id) to "$name now asks 40 gold.", vm.playing().sheet to vm.playing().notice)
        assertEquals(40, vm.playing().state.weapons.getValue(id).listedPrice)
        vm.stock(id, StockAction.Unlist); advanceUntilIdle()
        assertEquals(Sheet.Item(id) to "$name is back in storage, not for sale.", vm.playing().sheet to vm.playing().notice)
        val before = vm.playing().state.weapons.getValue(id)
        vm.stock(id, StockAction.Hone); advanceUntilIdle()
        val honed = vm.playing().state.weapons.getValue(id)
        assertNull(vm.playing().lastError)
        assertTrue(honed.honed)
        assertEquals(Sheet.Item(id) to "$name was honed: quality ${before.quality} to ${honed.quality}.", vm.playing().sheet to vm.playing().notice)

        // A refusal says why and claims nothing: the blade is honed already.
        vm.stock(id, StockAction.Hone); advanceUntilIdle()
        if (vm.playing().lastError != null) { assertTrue(vm.playing().notice!!.contains("was honed")); vm.dismissError(); advanceUntilIdle() }

        // Melted down: the blade is gone, and the sheet is closed on purpose with the reason left on screen.
        vm.stock(id, StockAction.Salvage); advanceUntilIdle()
        val s = vm.playing()
        assertTrue(s.state.weapons.getValue(id).location is WeaponLocation.Destroyed)
        assertEquals(null to "$name was melted down. 1 Iron is back in your stock.", s.sheet to s.notice)
    }
}
