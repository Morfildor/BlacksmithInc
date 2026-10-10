package com.tinyblacksmith.core

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import com.tinyblacksmith.core.sim.applySet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The `--customers` metrics and the `--set` overrides: counted by hand from the engine's own day resolutions, and read-only. */
class SimulatorMetricsTest {
    @Test
    fun metricsOnAFixedSeedEqualHandCountedValues() {
        val seed = 7L
        val engine = GameEngine()
        val cap = engine.config.customers.shopCapacity
        // The days as the engine resolved them: every post-End-Day state of a run that has no metrics attached.
        val posts = ArrayList<GameState>()
        SimulationDriver(engine, maxDays = 15, onDayResolved = { s, _ -> posts += s }).playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR)
        val withMetrics = SimulationDriver(engine, maxDays = 15, customerMetrics = true).playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR).first
        val m = assertNotNull(withMetrics.customers)

        var days = 0; var heroDays = 0; var visits = 0; var buys = 0; var atCap = 0; var twoOrFewer = 0; var turnedAway = 0
        val perCount = HashMap<Int, Int>(); val reasons = HashMap<String, Int>(); val served = HashSet<String>()
        var firstPositionVisits = 0; var firstNameDays = 0; var surnameDays = 0; var lost = 0; var fatal = 0; var won = 0
        var before = engine.newRun(LegacyProfile(), seed)
        for (after in posts) {
            val res = assertNotNull(after.lastResolution)
            val alive = before.aliveHeroes()
            // The festival crowd is there when the flag was set for today: last night by the old event, or this morning by the stall a visitor sold.
            val festival = before.worldFlags[WorldEvents.FLAG_FESTIVAL] == before.day || res.events.any { it.type == EventType.ENCOUNTER_RESOLVED && it.data["option"] == "stall" }
            days++; heroDays += alive.size
            visits += res.browsers.size; buys += res.browsers.count { it.purchasedWeaponId != null }
            perCount.merge(res.browsers.size, 1, Int::plus)
            if (res.browsers.size >= cap + (if (festival) engine.config.customers.festivalExtraSeats else 0)) atCap++
            if (res.browsers.size <= 2) twoOrFewer++
            turnedAway += res.turnedAway.size
            for (v in res.browsers) { reasons.merge(v.reason.name, 1, Int::plus); served += v.heroId!!.value }
            firstPositionVisits += res.browsers.count { it.heroId == alive.first().id }
            if (alive.map { it.name }.toSet().size < alive.size) firstNameDays++
            if (alive.map { it.surname }.toSet().size < alive.size) surnameDays++
            lost += res.events.count { it.type == EventType.EXPEDITION_LOST }
            fatal += res.events.count { it.type == EventType.HERO_DIED }
            won += res.events.count { it.type == EventType.ELITE_SLAIN || (it.type == EventType.EXPEDITION_WON && !it.data.containsKey("material")) }
            before = after
        }

        assertTrue(visits > 0 && buys > 0 && days > 3, "the fixed run should have customers: days=$days visits=$visits buys=$buys")
        assertEquals(days, m.days)
        assertEquals(heroDays, m.heroDays)
        assertEquals(visits, m.visits)
        assertEquals(buys, m.buys)
        assertEquals(atCap, m.capDays)
        assertEquals(twoOrFewer, m.lowDays)
        assertEquals(turnedAway, m.turnedAway)
        assertEquals(perCount, m.servedByCount.withIndex().filter { it.value > 0 }.associate { it.index to it.value })
        assertEquals(reasons.toSortedMap(), withMetrics.visitReasons)
        assertEquals(served.size, m.servedEver)
        assertEquals(firstPositionVisits, m.posVisits[0])
        assertEquals(heroDays, m.posHeroDays.sum())
        assertEquals(visits, m.posVisits.sum())
        assertEquals(firstNameDays, m.firstNameDays)
        assertEquals(surnameDays, m.surnameDays)
        assertEquals(won, m.expWon)
        assertEquals(lost, m.expLost)
        assertEquals(fatal, m.expFatal)
    }

    @Test
    fun metricsDoNotChangeAnyRun() {
        for (policy in listOf(Policy.BALANCED_FAIR, Policy.BALANCED_ACTIVE, Policy.SYNERGY, Policy.RANDOM, Policy.BALANCED_EXPENSIVE)) {
            for (seed in listOf(1L, 2L, 3L)) {
                val (plainStats, plainState) = SimulationDriver().playRun(LegacyProfile(), seed, policy)
                val (metricStats, metricState) = SimulationDriver(customerMetrics = true).playRun(LegacyProfile(), seed, policy)
                assertEquals(plainState, metricState, "final state differs for $policy seed $seed")
                assertEquals(plainStats.daysSurvived, metricStats.daysSurvived)
                assertNull(plainStats.customers)
                assertEquals(plainStats, metricStats.copy(customers = null), "stats differ for $policy seed $seed")
            }
        }
    }

    @Test
    fun summariesCarryCustomersOnlyWhenAsked() {
        val off = Simulator.run(5, 1, listOf(Policy.BALANCED_FAIR)).single().summary()
        val on = Simulator.run(5, 1, listOf(Policy.BALANCED_FAIR), customerMetrics = true).single().summary()
        assertNull(off.customers)
        val c = assertNotNull(on.customers)
        assertTrue(c.servedPerDay > 0.0 && c.livingHeroesPerDay >= 5.0)
        assertEquals(off, on.copy(customers = null))
    }

    @Test
    fun setOverridesAllowlistedKeysAndRejectsTheRest() {
        val c = applySet(BalanceConfig.DEFAULT, "shopCapacity=6,startingHeroes=12,baseVisitChance=0.4,raidPerDay=7.0,expeditionSuppression=2")
        assertEquals(6, c.customers.shopCapacity)
        assertEquals(12, c.customers.startingHeroes)
        assertEquals(0.4, c.customers.baseVisitChance)
        assertEquals(7.0, c.raidPerDay)
        assertEquals(2, c.expeditionSuppression)
        assertEquals(BalanceConfig.DEFAULT.copy(customers = BalanceConfig.DEFAULT.customers.copy(shopCapacity = 6, startingHeroes = 12, baseVisitChance = 0.4), raidPerDay = 7.0, expeditionSuppression = 2), c)
        assertEquals(c, applySet(BalanceConfig.DEFAULT, "maxCustomersPerDay=6,startingHeroCount=12,baseVisitChance=0.4,raidPerDay=7.0,expeditionSuppression=2"), "the names these numbers had before balance v7 still work")
        assertFailsWith<IllegalArgumentException> { applySet(BalanceConfig.DEFAULT, "baseDailyEnergy=12") }
        assertFailsWith<IllegalArgumentException> { applySet(BalanceConfig.DEFAULT, "shopCapacity=lots") }
        assertFailsWith<IllegalArgumentException> { applySet(BalanceConfig.DEFAULT, "shopCapacity") }
    }
}
