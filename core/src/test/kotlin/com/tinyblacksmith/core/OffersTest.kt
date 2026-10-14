package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.WeaponLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Offers on a premium listing (review 2026-10-14 gameplay batch, PROPOSED): a hero who wants a blade listed above what
 * they can or would pay offers what they would pay, never under its asking price; a sale on an offer earns
 * `customers.offerReputation`, and the record keeps the price that was asked.
 */
class OffersTest {
    private val config = engine.config

    private fun fixture(priceFactor: Double): Pair<ResolutionContext, com.tinyblacksmith.core.model.Weapon> {
        val made = engine.newRun(LegacyProfile(), 7).withMaterials().copy(gold = 1000).forgeAccepted(quickSword(Risk.SAFE))
        val blade = made.state.weapon(made.forgedWeaponId!!)
        val asked = (Market.askingPrice(blade, config) * priceFactor).toInt()
        val listed = blade.copy(location = WeaponLocation.Shelf(asked))
        // Purses that reach the asking price and no further: can pay the going rate, not the premium.
        val purse = Market.askingPrice(blade, config)
        val state = made.state.copy(weapons = made.state.weapons + (blade.id to listed), heroes = made.state.heroes.mapValues { (_, h) -> h.copy(gold = purse) })
        return ResolutionContext(state, engine.content, config) to listed
    }

    @Test
    fun aPremiumListingMeetsAnOfferNeverUnderTheAskingPrice() {
        val (ctx, blade) = fixture(1.8)
        val asking = Market.askingPrice(blade, config)
        var offers = 0
        for (hero in ctx.aliveHeroes()) {
            val e = Market.evaluate(ctx, hero, ctx.equippedWeapon(hero.id), blade, 0.5)
            if (e.offered) {
                offers++
                assertTrue(e.pays in asking until blade.listedPrice!!, "${hero.fullName} offers ${e.pays}, asking $asking, listed ${blade.listedPrice}")
                assertFalse(e.affordable, "can pay is the listed price; this hero offers instead")
            } else assertEquals(blade.listedPrice, e.pays)
        }
        assertTrue(offers > 0, "some hero can pay the asking price of a blade listed at 180 % of it")
        // Off: nobody offers, and the listed price is what each would have to pay.
        val off = ResolutionContext(ctx.base, engine.content, config.copy(customers = config.customers.copy(offers = false)))
        for (hero in ctx.aliveHeroes()) assertFalse(Market.evaluate(off, hero, ctx.equippedWeapon(hero.id), blade, 0.5).offered)
    }

    /** A tag beyond twice the asking price is not bargained over: the hero walks away as before. */
    @Test
    fun anAbsurdTagIsNotBargainedOver() {
        val (ctx, blade) = fixture(2.5)
        for (hero in ctx.aliveHeroes()) assertFalse(Market.evaluate(ctx, hero, ctx.equippedWeapon(hero.id), blade, 0.5).offered)
    }

    @Test
    fun aFairListingIsNeverMetWithAnOffer() {
        val (ctx, blade) = fixture(1.0)
        for (hero in ctx.aliveHeroes()) assertFalse(Market.evaluate(ctx, hero, ctx.equippedWeapon(hero.id), blade, 0.5).offered)
    }

    @Test
    fun aSaleOnAnOfferKeepsWhatWasAskedAndEarnsTheOfferReputation() {
        val (ctx, blade) = fixture(1.8)
        val hero = ctx.aliveHeroes().first { Market.evaluate(ctx, it, ctx.equippedWeapon(it.id), blade, 0.5).offered }
        val pays = Market.evaluate(ctx, hero, ctx.equippedWeapon(hero.id), blade, 0.5).pays
        val reputation = ctx.reputation
        val sale = Market.purchase(ctx, hero, blade, pays, asked = blade.listedPrice)
        assertEquals(blade.listedPrice, sale.asked)
        assertEquals(pays, sale.listedPrice)
        assertEquals(reputation + config.customers.offerReputation, ctx.reputation)
        // A sale at the listed price is not an offer and earns the sale reputation.
        val (ctx2, blade2) = fixture(1.0)
        val buyer = ctx2.aliveHeroes().first()
        val before = ctx2.reputation
        assertNull(Market.purchase(ctx2, buyer, blade2, blade2.listedPrice!!).asked)
        assertEquals(before + config.customers.saleReputation, ctx2.reputation)
    }
}
