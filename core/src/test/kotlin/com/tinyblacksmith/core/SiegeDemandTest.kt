package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.shopday.ShopDay
import com.tinyblacksmith.core.shopday.ThreatMark
import com.tinyblacksmith.core.shopday.Threats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E2, X17: under a siege warning the town wants the element the besieger fears and leaves the one it resists on the shelf. */
class SiegeDemandTest {
    private val base = TestSupport.engine.config
    private val config = base.copy(customers = base.customers.copy(baseVisitChance = 1.0, visitCeiling = 1.0))
    private val engine = GameEngine(config = config)
    private val content = engine.content
    private val cfg = config.customers
    private val fit = content.family(LaunchContent.SWORD).classFit.getValue(LaunchContent.GUARDIAN)

    private val forged = engine.newRun(LegacyProfile(), 7).withMaterials().forgeAccepted(quickSword(Risk.SAFE))
    private val start = forged.state
    private val besieger = content.faction(start.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure }!!.id)
    private val weak = besieger.weakTo!!
    private val resisted = besieger.resists!!
    private val plain = start.weapon(forged.forgedWeaponId!!).copy(affixes = emptyList(), flaws = emptyList(), element = null, power = 30, quality = 40)
    private val guardian = start.aliveHeroes().first { it.classId == LaunchContent.GUARDIAN }
        .copy(gold = 1000, elementTaste = null, ambition = null, loyalty = 0, traits = emptyList(), health = 100)

    /** A town of one: [hero] carrying [own], [shelf] listed at 60 gold, the siege [daysToSiege] days off. */
    private fun shop(daysToSiege: Int, hero: Hero, own: Weapon?, shelf: Weapon, champion: Boolean = false): GameState = start.copy(
        heroes = mapOf(hero.id to hero),
        weapons = listOfNotNull(own?.copy(id = WeaponId("w90"), location = WeaponLocation.Owned(hero.id, true)), shelf.copy(id = WeaponId("w1"), location = WeaponLocation.Shelf(60))).associateBy { it.id },
        town = start.town.copy(championIds = if (champion) listOf(hero.id) else emptyList(), nextSiegeDay = start.day + daysToSiege), commissions = emptyMap(),
    )

    private fun eval(s: GameState, with: com.tinyblacksmith.core.config.BalanceConfig = config, roll: Double = 0.5): Market.Evaluation {
        val ctx = ResolutionContext(s, content, with)
        val hero = ctx.aliveHeroes().single()
        return Market.evaluate(ctx, hero, ctx.equippedWeapon(hero.id), ctx.weapon(WeaponId("w1")), roll)
    }

    private fun GameState.close(): CommandOutcome.Accepted = engine.handle(this, Command.EndDay(TestSupport.endDayId(this))) as CommandOutcome.Accepted

    @Test
    fun counterElementIsValuedInTheWarningWindowOnly() {
        val counter = plain.copy(element = weak)
        // Warnings go out on the two evenings before a siege; the days shopped under them are the eve and the siege day.
        for ((days, warned) in listOf(4 to false, 3 to false, 2 to false, 1 to true, 0 to true)) {
            val s = shop(days, guardian, plain, counter)
            assertEquals(warned, Battle.warnedFaction(ResolutionContext(s, content, config)) != null, "$days days to the siege")
            assertEquals(warned, Threats.of(s, content, config)!!.warned)
            val e = eval(s)
            assertEquals(warned, e.countersThreat, "$days days to the siege")
            // Same power, same make: on a calm day it is no gain at all; under the warning it is worth the matchup more, and wanted.
            assertEquals(if (warned) 30 * fit * (config.matchupWeakBonus - 1.0) else 0.0, e.gain, 1e-9)
            assertEquals((if (warned) cfg.threatUtility + e.gain * config.utilityImprovementWeight else 0.0), e.utility - eval(shop(4, guardian, plain, counter)).utility, 1e-9)
            // A champion comes more readily while the warning is out, and only then.
            val ordinary = config.copy(customers = base.customers)
            fun chance(champion: Boolean) = shop(days, guardian, plain, counter, champion).let { Market.willingness(ResolutionContext(it, content, ordinary), guardian, false) }
            assertEquals(if (warned) base.customers.championSiegeWillingness else 0.0, chance(true) - chance(false), 1e-9)
        }
        // Bought under the warning with its own reason; the same shop four days out sells nothing.
        val sold = shop(1, guardian, plain, counter).close().resolution!!.browsers.single()
        assertEquals(VisitReason.COUNTERS_THREAT to WeaponId("w1"), sold.reason to sold.purchasedWeaponId)
        assertTrue(VisitFactor.COUNTERS_THREAT in sold.considered.first().factors)
        assertEquals(VisitReason.NOT_BETTER, shop(4, guardian, plain, counter).close().resolution!!.browsers.single().reason)
        // The switch: with the threat utility at 0 the warning changes nothing at the counter.
        val off = config.copy(customers = cfg.copy(threatUtility = 0.0))
        assertEquals(eval(shop(4, guardian, plain, counter), off), eval(shop(1, guardian, plain, counter), off))
        assertTrue(!Threats.of(shop(1, guardian, plain, counter), content, off)!!.warned)
    }

    @Test
    fun aResistedBladeIsRefusedWithItsOwnReason() {
        // Three points stronger than the blade in hand, and of the hero's favoured element: bought on any calm day.
        val hero = guardian.copy(elementTaste = resisted)
        val own = plain.copy(power = 27)
        val blade = plain.copy(element = resisted)
        val calm = shop(4, hero, own, blade)
        assertTrue(eval(calm, roll = 0.0).let { it.eligible && it.utility >= config.purchaseUtilityThreshold && !it.resisted }, "sells at the worst roll on a calm day")
        assertEquals(WeaponId("w1"), calm.close().resolution!!.browsers.single().purchasedWeaponId)

        // Under the warning the same blade is worth the resist penalty less in the hand: no gain, no sale, and the visit says why.
        val warned = shop(1, hero, own, blade)
        val e = eval(warned, roll = 0.0)
        assertEquals((30 * config.matchupResistPenalty - 27) * fit, e.gain, 1e-9)
        assertTrue(e.resisted && e.refusedAsResisted && !e.eligible)
        val out = warned.close()
        val visit = out.resolution!!.browsers.single()
        assertEquals(VisitReason.RESISTED, visit.reason)
        assertNull(visit.purchasedWeaponId)
        assertTrue(VisitFactor.THREAT_RESISTS in visit.considered.first().factors)
        val told = Lines.decision(visit, ShopDay.script(out.resolution!!, out.state, content, config), content)
        assertTrue("shrugs off" in told && "%" !in told, told)
        assertNotNull(out.state.hero(hero.id).want, "a refusal like any other: it leaves a want")

        // Not every refusal of a resisted blade is RESISTED: one they would not have bought anyway keeps its old reason.
        assertEquals(VisitReason.NOT_BETTER, shop(1, hero, plain.copy(power = 60), blade).close().resolution!!.browsers.single().reason)
        // A much stronger resisted blade still sells: the warning lowers its worth, it does not forbid it.
        assertEquals(WeaponId("w1"), shop(1, hero, own, blade.copy(power = 45)).close().resolution!!.browsers.single().purchasedWeaponId)

        // The labels the Forge and the shelf show, from one function.
        val threat = Threats.of(warned, content, config)!!
        assertEquals(listOf(besieger.id, weak, resisted, 1, true), listOf(threat.factionId, threat.weakTo, threat.resists, threat.daysToSiege, threat.warned))
        assertEquals(ThreatMark.RESISTED, Threats.mark(blade.element, threat))
        assertEquals(ThreatMark.COUNTERS, Threats.mark(weak, threat))
        assertNull(Threats.mark(null, threat))
        val line = Lines.threat(threat, content)!!
        assertTrue(besieger.name in line && weak.name.lowercase() in line.lowercase() && resisted.name.lowercase() in line && "%" !in line, line)
        assertTrue(besieger.name in Lines.threatMark(ThreatMark.COUNTERS, threat, content))
    }
}
