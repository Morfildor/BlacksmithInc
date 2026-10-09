package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G01: the gain is not rounded, affixes and fame count on both sides, and a near-equal blade can be bought once for a side reason. */
class SidegradeTest {
    private val base = TestSupport.engine.config
    /** One customer who always comes: the counter's rule is what is under test, not the door. */
    private val config = base.copy(customers = base.customers.copy(baseVisitChance = 1.0, visitCeiling = 1.0))
    private val engine = GameEngine(config = config)
    private val content = engine.content
    private val sides = setOf(VisitReason.TASTE_MATCH, VisitReason.PRIZED, VisitReason.STORIED)
    /** A guardian's class fit for a sword: every worth below is the blade's power times this. */
    private val fit = content.family(LaunchContent.SWORD).classFit.getValue(LaunchContent.GUARDIAN)

    private val forged = engine.newRun(LegacyProfile(), 7).withMaterials().forgeAccepted(quickSword(Risk.SAFE))
    private val plain = forged.state.weapon(forged.forgedWeaponId!!).copy(affixes = emptyList(), flaws = emptyList(), element = null, power = 30, quality = 40, fame = 0)
    private val guardian = forged.state.aliveHeroes().first { it.classId == LaunchContent.GUARDIAN }
        .copy(gold = 1000, elementTaste = Element.FROST, ambition = null, loyalty = 0, traits = emptyList(), health = 100)

    /** A town of one: [hero] carrying [own], the [shelf] blades listed at 60 gold, no siege near. */
    private fun shop(hero: Hero, own: Weapon?, vararg shelf: Weapon): GameState = forged.state.copy(
        heroes = mapOf(hero.id to hero),
        weapons = listOfNotNull(own?.copy(id = WeaponId("w90"), location = WeaponLocation.Owned(hero.id, true))).associateBy { it.id } +
            shelf.withIndex().associate { (i, w) -> WeaponId("w${i + 1}") to w.copy(id = WeaponId("w${i + 1}"), location = WeaponLocation.Shelf(60)) },
        town = forged.state.town.copy(championIds = emptyList(), nextSiegeDay = forged.state.day + 4), commissions = emptyMap(),
    )

    private fun eval(s: GameState, shelf: String = "w1", roll: Double = 0.5): Market.Evaluation {
        val ctx = ResolutionContext(s, content, config)
        val hero = ctx.aliveHeroes().single()
        return Market.evaluate(ctx, hero, ctx.equippedWeapon(hero.id), ctx.weapon(WeaponId(shelf)), roll)
    }

    private fun GameState.close(): CommandOutcome.Accepted = engine.handle(this, Command.EndDay(TestSupport.endDayId(this))) as CommandOutcome.Accepted

    @Test
    fun aNearEqualBladeIsBoughtForTasteOnce() {
        val frost = plain.copy(power = 29, element = Element.FROST)
        val s = shop(guardian, plain, frost)
        val e = eval(s)
        assertTrue(e.gain < 0 && e.gain >= -config.customers.sidegradeTolerance * 30 * fit, "one point weaker of thirty is near-equal (${e.gain})")
        assertEquals(VisitReason.TASTE_MATCH, e.sideReason)
        assertTrue(e.eligible)

        val out = s.close()
        val visit = out.resolution!!.browsers.single()
        assertEquals(VisitReason.TASTE_MATCH to WeaponId("w1"), visit.reason to visit.purchasedWeaponId)
        assertEquals(setOf(VisitReason.TASTE_MATCH), out.state.hero(guardian.id).sideReasons)
        assertTrue(out.state.weapon(WeaponId("w90")).isInStorage, "the old blade came back in part payment")

        // Once: the same hero, holding a plain blade again, is no longer moved by taste alone.
        val again = shop(out.state.hero(guardian.id), plain, frost)
        assertNull(eval(again).sideReason)
        assertEquals(VisitReason.NOT_BETTER, again.close().resolution!!.browsers.single().reason)

        // One-way: a hero whose own blade already has the element gets no side reason, and neither does a blade further off than the tolerance.
        assertNull(eval(shop(guardian, plain.copy(element = Element.FROST), frost)).sideReason)
        assertNull(eval(shop(guardian, plain, frost.copy(power = 26))).sideReason)
        // The other two reasons: a prize for an unfulfilled collector, a blade with a name.
        val collector = guardian.copy(elementTaste = null, ambition = Ambition.COLLECTOR)
        assertEquals(VisitReason.PRIZED, eval(shop(collector, plain, plain.copy(power = 29, quality = config.ambitionCollectorQuality))).sideReason)
        assertNull(eval(shop(collector.copy(ambitionDone = true), plain, plain.copy(power = 29, quality = config.ambitionCollectorQuality))).sideReason)
        // (a storied blade fights a little better, so its power is set a little lower to stay just under the plain one)
        assertEquals(VisitReason.STORIED, eval(shop(guardian.copy(elementTaste = null), plain, plain.copy(power = 29, fame = config.legendFameThreshold))).sideReason)
        // The switch: tolerance 0 buys gains only.
        val off = config.copy(customers = config.customers.copy(sidegradeTolerance = 0.0))
        val ctx = ResolutionContext(s, content, off)
        assertNull(Market.evaluate(ctx, guardian, ctx.equippedWeapon(guardian.id), ctx.weapon(WeaponId("w1")), 0.5).sideReason)
    }

    @Test
    fun noChurn() {
        // Forty days with both blades always on offer and a purse that never empties: each side reason moves the hero once
        // at most (taste; later a name, if the frost blade earns one in their hands), however often the pair is on the shelf.
        val frost = plain.copy(power = 29, element = Element.FROST)
        for (seed in 1L..5L) {
            var s = shop(guardian, plain, frost).let { it.copy(rng = engine.newRun(LegacyProfile(), seed).rng) }
            val reasons = mutableListOf<VisitReason>()
            for (n in 1..40) {
                val out = s.close()
                reasons += out.resolution!!.browsers.map { it.reason }
                if (out.state.isEnded || !out.state.hero(guardian.id).isAlive) break
                // Next morning: whatever came back is listed again and the purse is refilled; the forge never falls.
                s = out.state.copy(
                    weapons = out.state.weapons.mapValues { (_, w) -> if (w.isInStorage) w.copy(location = WeaponLocation.Shelf(60), condition = 100) else w.copy(condition = 100) },
                    heroes = mapOf(guardian.id to out.state.hero(guardian.id).copy(gold = 1000, health = 100)),   // last night's newcomers move on
                    town = out.state.town.copy(integrity = 100, nextSiegeDay = out.state.day + 4, championIds = emptyList()),
                )
            }
            val hero = s.heroes.getValue(guardian.id)
            val bought = reasons.filter { it in sides }
            assertEquals(VisitReason.TASTE_MATCH, bought.first(), "seed $seed: $reasons")
            assertEquals(bought.toSet().size, bought.size, "seed $seed: no reason twice: $reasons")
            assertEquals(bought.toSet(), hero.sideReasons, "seed $seed")
            assertTrue(bought.size <= sides.size && VisitReason.PRIZED !in bought, "seed $seed: $reasons")
            // Every sidegrade is answered by at most one purchase back for the gain: the pair cannot ping-pong.
            assertTrue(reasons.count { it == VisitReason.GREAT_FIT || it == VisitReason.GOOD_ENOUGH || it == VisitReason.WORN_OUT } <= bought.size, "seed $seed: $reasons")
        }
    }

    @Test
    fun aNineTenthsGainIsAGain() {
        // 30 x 1.03 (six counted points of fame) against 30: a gain of 0.9 that whole numbers used to hide.
        val e = eval(shop(guardian.copy(elementTaste = null), plain, plain.copy(fame = 6)), roll = 1.0)
        assertEquals(0.9 * fit, e.gain, 1e-9)
        assertTrue(e.eligible && e.sideReason == null, "a gain needs no side reason")
        assertTrue(e.utility >= config.purchaseUtilityThreshold, "and with a kind roll it sells (${e.utility})")
        // A hair under is not.
        assertTrue(!eval(shop(guardian.copy(elementTaste = null), plain.copy(fame = 6), plain.copy(fame = 5))).eligible)
    }

    @Test
    fun affixesAndFameCountOnBothSides() {
        val keen = content.affixes.first { it.attackMultiplier > 1.0 }
        val hero = guardian.copy(elementTaste = null)
        fun gain(own: Weapon, shelf: Weapon) = eval(shop(hero, own, shelf)).gain
        assertEquals(0.0, gain(plain, plain), 1e-9)
        assertEquals(30 * fit * (keen.attackMultiplier - 1.0), gain(plain, plain.copy(affixes = listOf(keen.id))), 1e-9, "the affix on the shelf blade")
        assertEquals(-30 * fit * (keen.attackMultiplier - 1.0), gain(plain.copy(affixes = listOf(keen.id)), plain), 1e-9, "and on the one in hand")
        assertEquals(0.0, gain(plain.copy(affixes = listOf(keen.id)), plain.copy(affixes = listOf(keen.id))), 1e-9)
        val famed = 30 * fit * config.weaponFameCap * config.weaponFamePowerPerPoint
        assertEquals(famed, gain(plain, plain.copy(fame = 50)), 1e-9, "fame counts to its cap")
        assertEquals(-famed, gain(plain.copy(fame = 50), plain), 1e-9)
        // Wear and class fit as before, on both sides.
        assertTrue(gain(plain.copy(condition = 10), plain) > 0 && gain(plain, plain.copy(condition = 10)) < 0)
        assertEquals(Market.valueInHand(ResolutionContext(shop(hero, null, plain), content, config), hero, null, null), config.unarmedPower.toDouble(), 1e-9)
    }
}
