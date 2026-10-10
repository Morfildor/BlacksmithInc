package com.example.blacksmithproject

import com.example.blacksmithproject.ui.Labels
import com.example.blacksmithproject.ui.detail.affordLine
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.detail.priceAgainstSuggested
import com.example.blacksmithproject.ui.forgedFor
import com.example.blacksmithproject.ui.shopUi
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.shopday.Demand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forge, choose a price, list or store: what the forge result is given, what its two buttons do, and what the workshop then says. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForgeResultTest : ShopDayTestBase() {
    private val forged: GameState = (engine.handle(fresh, forgeSword) as CommandOutcome.Accepted).state
    private val blade: Weapon = forged.storedWeapons().single()

    /** The shelf of [forged] filled to its last slot with copies of the blade, priced out of everyone's reach. */
    private fun fullShelf(): GameState {
        val listed = (1..engine.shelfSlots(forged)).map { blade.copy(id = WeaponId("full$it"), location = WeaponLocation.Shelf(9_999)) }
        return forged.copy(weapons = forged.weapons + listed.associateBy { it.id })
    }

    private fun request(status: CommissionStatus, minQuality: Int = 1) =
        Commission(CommissionId("c1"), forged.aliveHeroes().first().id, blade.familyId, minQuality, 80, forged.day, forged.day + 3, status)

    @Test
    fun listingAtAChosenPriceSavesItAndSaysWhereTheBladeWent() = vmTest {
        val vm = open(repo(fresh))
        vm.dispatch(forgeSword); advanceUntilIdle()
        val id = vm.playing().revealWeaponId!!
        val suggested = engine.suggestedPrice(vm.playing().state.weapons.getValue(id))
        val chosen = suggested - 30

        vm.listForged(id, chosen); advanceUntilIdle()
        val s = vm.playing()
        assertEquals("the price on the shelf is the one chosen, not the suggested one", chosen, s.state.weapons.getValue(id).listedPrice)
        assertNull("the card closes once the listing is saved", s.revealWeaponId)
        assertEquals("${blade.name} is on the shelf at $chosen gold. Shelf 1 of ${engine.shelfSlots(s.state)}.", s.notice)
        assertEquals(listOf(id to chosen), s.shop.shelf.map { it.weapon.id to it.price })

        // Shown once: an older notice does not clear a newer one.
        vm.dismissNotice("something older"); advanceUntilIdle()
        assertNotNull(vm.playing().notice)
        vm.dismissNotice(s.notice!!); advanceUntilIdle()
        assertNull(vm.playing().notice)
    }

    @Test
    fun storingLeavesTheBladeInStorageAndSaysSo() = vmTest {
        val vm = open(repo(fresh))
        vm.dispatch(forgeSword); advanceUntilIdle()
        val id = vm.playing().revealWeaponId!!

        vm.storeForged(); advanceUntilIdle()
        val s = vm.playing()
        assertNull(s.revealWeaponId)
        assertTrue("nothing was listed", s.state.weapons.getValue(id).isInStorage && s.state.listedWeapons().isEmpty())
        assertEquals("${blade.name} is in storage, not for sale. List it from Storage in the Shop.", s.notice)
    }

    /** The screen is told the shelf is full before the tap; if a listing is refused all the same, the card stays open under the engine's reason. */
    @Test
    fun aFullShelfIsKnownBeforeTheTapAndARefusalKeepsTheCardOpen() = vmTest {
        val full = fullShelf()
        val stock = engine.itemDetail(full, blade.id)!!.stock!!
        assertEquals(0 to engine.shelfSlots(full), stock.shelfFree to stock.slots)
        assertEquals(engine.shelfSlots(forged) to engine.shelfSlots(forged), engine.itemDetail(forged, blade.id)!!.stock!!.let { it.shelfFree to it.slots })

        val vm = open(repo(full))
        vm.dispatch(forgeSword); advanceUntilIdle()
        val id = vm.playing().revealWeaponId!!
        vm.listForged(id, 40); advanceUntilIdle()
        val s = vm.playing()
        assertEquals("the card is still open", id, s.revealWeaponId)
        assertEquals("All ${engine.shelfSlots(full)} shelf slots are full.", s.lastError)
        assertNull("nothing went anywhere, so nothing is announced", s.notice)
        assertTrue(s.state.weapons.getValue(id).isInStorage)
        // Store is still the way on.
        vm.dismissError(); vm.storeForged(); advanceUntilIdle()
        assertNull(vm.playing().revealWeaponId)
        assertTrue(vm.playing().notice!!.contains("in storage"))
    }

    /** One count on every surface: the Shop's model, the blade sheet and the forge result read `Demand.funds`. */
    @Test
    fun thePriceLineCountsTheSavedPursesByTheCountersRule() {
        val purses = forged.copy(heroes = forged.heroes.mapValues { (_, h) -> h.copy(gold = 10 * (h.id.value.filter(Char::isDigit).toInt() % 7)) })
        val funds = Demand.funds(purses, engine.content, engine.config)
        assertEquals(funds, engine.shopUi(purses).funds)
        assertEquals(funds, engine.itemDetail(purses, blade.id)!!.stock!!.funds)
        assertEquals(purses.aliveHeroes().size, funds.size)
        assertTrue("the scenario: purses differ", funds.distinct().size > 2)
        for (price in listOf(0, 10, 35, 60, 61, 9_999)) {
            assertEquals("${funds.count { it >= price }} of ${funds.size} heroes in town can afford this price.", affordLine(funds, price))
        }
        assertEquals("0 of ${funds.size} heroes in town can afford this price.", affordLine(funds, 9_999))
        // "Who is buying" on the Shop agrees with the line for the same price.
        val listed = purses.copy(weapons = purses.weapons + (blade.id to blade.copy(location = WeaponLocation.Shelf(35))))
        assertEquals(Demand.canAfford(funds, 35), Demand.summary(listed, engine.content, engine.config).canAffordCheapest)
    }

    @Test
    fun theSuggestedPriceIsNamedApartFromTheChosenOne() {
        assertEquals("Suggested price 120 gold. Your price matches it.", priceAgainstSuggested(120, 120))
        assertEquals("Suggested price 120 gold. Your price is 30 below it.", priceAgainstSuggested(90, 120))
        assertEquals("Suggested price 120 gold. Your price is 15 above it.", priceAgainstSuggested(135, 120))
        assertEquals("Suggested price 120 gold. Enter your own price.", priceAgainstSuggested(null, 120))
    }

    /** A blade forged for a request: whether this blade fits, and what End Day will really hand over. Never "reserved". */
    @Test
    fun aBladeForgedForARequestSaysItsRealReadiness() {
        fun with(c: Commission, extra: List<Weapon> = emptyList()) = forged.copy(commissions = mapOf(c.id to c), weapons = forged.weapons + extra.associateBy { it.id })
        val buyer = forged.aliveHeroes().first().fullName

        // Accepted and fitting: the engine's own readiness line, which names the blade End Day will take.
        val accepted = request(CommissionStatus.ACCEPTED)
        val ready = engine.forgedFor(with(accepted), blade.id, accepted.id)!!
        assertEquals("For $buyer: ${Labels.request(accepted, engine.content, engine.config)}", ready.title)
        assertTrue(ready.terms.startsWith("80 gold · due "))
        assertTrue(ready.fits && ready.accepted)
        assertEquals("This blade fits the request", ready.fit)
        assertEquals("Ready: ${blade.name} will be handed over at End Day.", ready.handover)

        // A lesser blade that also fits is what the engine hands over: the card names that one, not the fresh blade.
        val lesser = blade.copy(id = WeaponId("lesser"), name = "Plain Old Sword", quality = blade.quality - 1)
        val two = with(accepted, listOf(lesser))
        assertEquals(lesser.id, Commissions.pick(two.weapons.values, accepted, engine.config)!!.id)
        engine.forgedFor(two, blade.id, accepted.id)!!.let {
            assertTrue("the fresh blade fits", it.fits)
            assertEquals("Ready: Plain Old Sword will be handed over at End Day.", it.handover)
        }

        // Too poor for the request: said of this blade, with what the shop still lacks.
        val demanding = request(CommissionStatus.ACCEPTED, minQuality = blade.quality + 5)
        engine.forgedFor(with(demanding), blade.id, demanding.id)!!.let {
            assertFalse(it.fits)
            assertEquals("This blade does not fit: quality ${blade.quality}, needs ${blade.quality + 5}", it.fit)
            assertEquals(Labels.readiness(demanding, with(demanding).weapons.values, engine.content, engine.config), it.handover)
            assertTrue(it.handover.startsWith("Nothing fits yet"))
        }

        // Offered but not accepted: End Day hands nothing over, and the card says so.
        val offered = request(CommissionStatus.OFFERED)
        engine.forgedFor(with(offered), blade.id, offered.id)!!.let {
            assertTrue(it.fits)
            assertFalse(it.accepted)
            assertEquals("Not accepted yet. Accept the request in the Shop, or nothing is handed over.", it.handover)
        }

        // No request, or one that is closed: nothing is shown.
        assertNull(engine.forgedFor(forged, blade.id, null))
        assertNull(engine.forgedFor(with(request(CommissionStatus.DECLINED)), blade.id, CommissionId("c1")))
        assertNull(engine.forgedFor(with(accepted), WeaponId("gone"), accepted.id))
    }
}
