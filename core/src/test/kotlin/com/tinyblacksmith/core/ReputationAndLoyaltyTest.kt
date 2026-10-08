package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GDD 5 "Reputation": willingness to pay follows shop reputation; loyalty drives repeat customers (docs/DECISIONS.md session 4). */
class ReputationAndLoyaltyTest {
    private val config = engine.config

    /** A fresh run with one safe sword listed at [factor] x the fair price; every hero gets [loyalty] and [gold]. */
    private fun listed(seed: Long, factor: Double, loyalty: Int = 0, reputation: Int = 0, gold: Int = 10_000): Pair<GameState, WeaponId> {
        val s0 = engine.newRun(LegacyProfile(), seed).withMaterials()
        val out = s0.forgeAccepted(quickSword(Risk.SAFE))
        val id = out.forgedWeaponId!!
        val price = (engine.suggestedPrice(out.state.weapon(id)) * factor).toInt()
        val s = out.state.copy(reputation = reputation, heroes = out.state.heroes.mapValues { it.value.copy(gold = gold, loyalty = loyalty) })
        return s.run(Command.ToggleShelf(id, true, price)) to id
    }

    @Test
    fun priceCeilingMultiplierStaysInsideItsCaps() {
        assertEquals(1.0, Market.priceCeilingMultiplier(0, 0, config))
        assertEquals(1.0, Market.priceCeilingMultiplier(-5, -5, config), "negative inputs never discount below fair")
        assertEquals(1.0 + config.reputationPriceCap, Market.priceCeilingMultiplier(100_000, 0, config))
        assertEquals(1.0 + config.loyaltyPriceCap, Market.priceCeilingMultiplier(0, 100_000, config))
        assertEquals(1.0 + config.reputationPriceCap + config.loyaltyPriceCap, Market.priceCeilingMultiplier(100_000, 100_000, config))
        assertTrue(1.0 + config.reputationPriceCap + config.loyaltyPriceCap <= 1.6, "sixfold prices must stay unsellable")
    }

    @Test
    fun loyalHeroToleratesAPriceAStrangerRejectsAtEqualGold() {
        val (s, id) = listed(seed = 3, factor = 1.2)
        val ctx = ResolutionContext(s, engine.content, config)
        val weapon = ctx.weapon(id)
        val stranger = ctx.aliveHeroes().first()
        val regular = stranger.copy(loyalty = 10)
        val a = Market.evaluate(ctx, stranger, null, weapon, 0.5)
        val b = Market.evaluate(ctx, regular, null, weapon, 0.5)
        assertEquals(a.affordable, b.affordable)
        assertTrue(a.pricePenalty > 0.0, "a stranger sees 120 % of fair as overpriced")
        assertEquals(0.0, b.pricePenalty, "a regular's ceiling covers 120 % of fair")
        assertTrue(b.utility > a.utility)
    }

    @Test
    fun reputationRaisesEveryHeroesWillingnessToPayButNeverToSixfold() {
        val (s, id) = listed(seed = 4, factor = 1.2)
        val ctx = ResolutionContext(s, engine.content, config)
        val weapon = ctx.weapon(id)
        val hero = ctx.aliveHeroes().first()
        val unknownShop = Market.evaluate(ctx, hero, null, weapon, 0.5)
        ctx.reputation = 25
        val famousShop = Market.evaluate(ctx, hero, null, weapon, 0.5)
        assertTrue(unknownShop.pricePenalty > 0.0)
        assertEquals(0.0, famousShop.pricePenalty)
        assertTrue(famousShop.utility > unknownShop.utility)
        // Bounds in play: maxed reputation and a devoted regular (loyalty well past the cap) still reject a sixfold price.
        ctx.reputation = 100_000
        val sixfold = ctx.weapon(id).copy(location = WeaponLocation.Shelf(engine.suggestedPrice(weapon) * 6))
        val greedyShop = Market.evaluate(ctx, hero.copy(loyalty = 30), null, sixfold, 1.0)
        assertTrue(greedyShop.utility < config.purchaseUtilityThreshold, "utility ${greedyShop.utility} must stay below the purchase threshold")
    }

    @Test
    fun loyalCustomersBuyPremiumListingsMoreOftenThanStrangers() {
        // 190 % of fair sits between a stranger's ceiling (100 %) and a capped regular's (125 %) once the penalty is weighed.
        var strangers = 0
        var regulars = 0
        for (seed in 1L..40L) {
            if (listed(seed, factor = 1.9, loyalty = 0).let { (s, id) -> s.endDay().weapon(id).ownerId != null }) strangers++
            if (listed(seed, factor = 1.9, loyalty = 10).let { (s, id) -> s.endDay().weapon(id).ownerId != null }) regulars++
        }
        assertTrue(regulars >= strangers + 8, "regulars bought $regulars/40, strangers $strangers/40")
    }

    @Test
    fun premiumSalesAndRegularsAreRecordedInTheEventLog() {
        var seen = false
        for (seed in 1L..40L) {
            val (s, id) = listed(seed, factor = 1.2, loyalty = 10, reputation = 25)
            val after = s.endDay()
            val sale = after.events.firstOrNull { it.type == EventType.WEAPON_SOLD && id.value in it.subjectIds } ?: continue
            val premium = sale.data.getValue("premium").toInt()
            assertEquals(sale.data.getValue("price").toInt() - engine.suggestedPrice(after.weapon(id)), premium)
            assertTrue(premium > 0)
            assertTrue("a regular of the shop" in sale.text, sale.text)
            assertTrue("above the going rate" in sale.text, sale.text)
            seen = true
            break
        }
        assertTrue(seen, "no premium sale in 40 seeds")
        // A fair-priced sale by a stranger carries neither marker.
        var fairSeen = false
        for (seed in 1L..40L) {
            val (s, id) = listed(seed, factor = 1.0)
            val sale = s.endDay().events.firstOrNull { it.type == EventType.WEAPON_SOLD && id.value in it.subjectIds } ?: continue
            assertTrue("premium" !in sale.data && "regular" !in sale.text, sale.text)
            fairSeen = true
            break
        }
        assertTrue(fairSeen, "no fair-priced sale in 40 seeds")
    }

    @Test
    fun regularsAreOfferedCommissionsMoreOftenThanUniformChance() {
        var offers = 0
        var toRegular = 0
        for (seed in 1L..60L) {
            var s = engine.newRun(LegacyProfile(), seed)
            val regular = s.aliveHeroes().first().id
            s = s.copy(heroes = s.heroes.mapValues { (id, h) -> h.copy(loyalty = if (id == regular) 10 else 0) })
            while (!s.isEnded && s.day < 15) {
                s = s.endDay()
                val offer = s.commissions.values.firstOrNull() ?: continue
                offers++
                if (offer.buyerId == regular) {
                    toRegular++
                    assertTrue(s.events.any { it.type == EventType.COMMISSION_OFFERED && "a regular of the shop" in it.text })
                }
                break
            }
        }
        assertTrue(offers >= 40, "offers: $offers")
        // Weight 1 + 10 * 0.5 = 6 against seven strangers at 1 each: expected 6/13 = 46 %; uniform would be 12.5 %.
        assertTrue(toRegular * 100 / offers >= 30, "regular got $toRegular of $offers offers")
    }

    @Test
    fun reputationAndLoyaltyEffectsAreDeterministic() {
        fun play(): String {
            var (s, _) = listed(seed = 11, factor = 1.2, loyalty = 6, reputation = 25, gold = 300)
            repeat(8) { if (!s.isEnded) s = s.endDay() }
            return SaveCodec.encodeRun(s)
        }
        assertEquals(play(), play())
    }
}
