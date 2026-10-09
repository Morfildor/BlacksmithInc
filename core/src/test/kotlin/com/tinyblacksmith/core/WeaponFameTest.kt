package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Balance v4 (pending): weapon fame as a bounded mechanical effect (GDD 7 PROPOSED). */
class WeaponFameTest {
    private val config = engine.config
    private val content = engine.content
    private val cap = config.weaponFameCap

    /** A fresh run with one forged sword listed at its suggested price. */
    private fun listed(seed: Long = 5): Pair<GameState, WeaponId> {
        val out = engine.newRun(LegacyProfile(), seed).withMaterials().forgeAccepted(quickSword(Risk.SAFE))
        val id = out.forgedWeaponId!!
        return out.state.run(Command.ToggleShelf(id, true, engine.suggestedPrice(out.state.weapon(id)))) to id
    }

    @Test
    fun fameFactorIsBoundedAndNeverCompounds() {
        assertEquals(1.0, Power.fameFactor(null, config))
        val (s, id) = listed()
        val plain = s.weapon(id).copy(fame = 0)
        assertEquals(1.0, Power.fameFactor(plain, config))
        assertEquals(1.0, Power.fameFactor(plain.copy(fame = -4), config), "negative fame never weakens")
        assertEquals(1.0 + 3 * config.weaponFamePowerPerPoint, Power.fameFactor(plain.copy(fame = 3), config), 1e-9)
        val atCap = Power.fameFactor(plain.copy(fame = cap), config)
        assertEquals(1.0 + cap * config.weaponFamePowerPerPoint, atCap, 1e-9)
        assertEquals(atCap, Power.fameFactor(plain.copy(fame = 1000), config), "fame past the cap is story only")
        assertTrue(atCap <= 1.10 + 1e-9, "the fame effect stays a small bounded bonus")
        // Applied once, as a plain multiplier, to both attack and defense.
        val hero = s.aliveHeroes().first()
        val faction = content.faction(LaunchContent.ASHCLAW)
        val famous = plain.copy(fame = 1000)
        assertEquals(Power.attackPower(hero, plain, faction, content, config) * atCap, Power.attackPower(hero, famous, faction, content, config), 1e-9)
        assertEquals(Power.defensePower(hero, plain, faction, content, config) * atCap, Power.defensePower(hero, famous, faction, content, config), 1e-9)
    }

    @Test
    fun famousBladeOutsellsAnIdenticalUnfamousOneAndCollectorsWantItMost() {
        val (s, id) = listed()
        val ctx = ResolutionContext(s, content, config)
        val plain = ctx.weapon(id).copy(fame = 0)
        val hero = ctx.aliveHeroes().first().copy(ambition = Ambition.SLAYER, ambitionDone = false)
        fun utility(w: Weapon, h: Hero = hero) = Market.evaluate(ctx, h, null, w, 0.5).utility
        val base = utility(plain)
        val storied = utility(plain.copy(fame = 4))
        val capped = utility(plain.copy(fame = cap))
        assertTrue(storied > base, "fame adds desire")
        assertTrue(capped > storied)
        assertEquals(capped, utility(plain.copy(fame = 1000)), 1e-9, "no desire past the cap")
        assertEquals(base + cap * config.weaponFameUtilityPerPoint, capped, 1e-9)
        assertEquals(base, utility(plain.copy(fame = -3)), 1e-9)
        // Same price, same power: fame changes desire only, never whether the blade counts as an upgrade.
        assertEquals(Market.evaluate(ctx, hero, null, plain, 0.5).improvement, Market.evaluate(ctx, hero, null, plain.copy(fame = cap), 0.5).improvement)
        val collector = hero.copy(ambition = Ambition.COLLECTOR, ambitionDone = false)
        val collectorGain = utility(plain.copy(fame = cap), collector) - utility(plain, collector)
        assertEquals(cap * config.weaponFameUtilityPerPoint * config.collectorFameMultiplier, collectorGain, 1e-9)
        assertEquals(collectorGain, utility(plain.copy(fame = 1000), collector) - utility(plain, collector), 1e-9, "a collector's extra desire is capped too")
        assertTrue(collectorGain <= 1.0 + 1e-9)
    }

    @Test
    fun suggestedPricePremiumIsSmallAndCapped() {
        val (s, id) = listed()
        val plain = s.weapon(id).copy(fame = 0)
        val fair = plain.power * config.fairGoldPerPower
        assertEquals(fair, engine.suggestedPrice(plain))
        val atCap = engine.suggestedPrice(plain.copy(fame = cap))
        assertEquals((fair * (1.0 + cap * config.weaponFamePricePerPoint)).toInt(), atCap)
        assertTrue(atCap > fair && atCap <= fair * 1.1, "premium $atCap over $fair stays small")
        assertEquals(atCap, engine.suggestedPrice(plain.copy(fame = 1000)), "no premium past the cap")
        assertEquals(fair, engine.suggestedPrice(plain.copy(fame = -2)))
        // Listed at its own suggested price, a famous blade still rates at least as well as a plain one at the fair price
        // for the thriftiest buyer (Greedy, saving a fortune, no loyalty): the premium never outweighs the fame bonus.
        val ctx = ResolutionContext(s, content, config)
        val thrifty = ctx.aliveHeroes().first().copy(traits = listOf(LaunchContent.GREEDY), ambition = Ambition.FORTUNE, ambitionDone = false, loyalty = 0, gold = 10_000)
        val plainListed = plain.copy(location = WeaponLocation.Shelf(fair))
        val famousListed = plain.copy(fame = cap, location = WeaponLocation.Shelf(atCap))
        assertTrue(Market.evaluate(ctx, thrifty, null, famousListed, 0.5).utility >= Market.evaluate(ctx, thrifty, null, plainListed, 0.5).utility)
    }

    @Test
    fun returnedLegendKeepsItsFameAndRespectsTheCap() {
        val legend = LegendEntry(
            era = 1, weaponName = "Old Ember", title = "Bane of the Ashclaw Raiders", kills = 40, fame = cap * 3, owners = listOf("Mira Vance"),
            familyId = LaunchContent.SWORD, coreId = LaunchContent.IRON, augmentId = LaunchContent.EMBER_RESIN, quality = 80, power = 30,
        )
        val ctx = ResolutionContext(engine.newRun(LegacyProfile(legendBoard = listOf(legend)), 1), content, config)
        WorldEvents.fire(ctx, WorldEvents.byId("famous_blade"))
        val s = ctx.toState()
        assertEquals(emptyList(), Invariants.check(s, config))
        val blade = s.weapons.values.single()
        assertEquals(legend.fame, blade.fame, "the story comes back whole")
        assertEquals(1.0 + cap * config.weaponFamePowerPerPoint, Power.fameFactor(blade, config), 1e-9, "the effect does not")
        assertEquals(engine.suggestedPrice(blade.copy(fame = cap)), engine.suggestedPrice(blade))
        assertTrue(blade.power < legend.power, "still returns dented")
        assertTrue(s.events.any { it.type == EventType.ARTIFACT_RETURNED && blade.id.value in it.subjectIds })
    }

    @Test
    fun fameEffectsAreDeterministic() {
        fun play(): String {
            var (s, id) = listed(seed = 9)
            // A storied blade on the shelf from day one, so fame is in play in the market and in the field.
            s = s.copy(weapons = s.weapons + (id to s.weapon(id).copy(fame = cap)))
            repeat(8) { if (!s.isEnded) s = s.endDay() }
            return SaveCodec.encodeRun(s)
        }
        assertEquals(play(), play())
    }
}
