package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plan 4.1 (N02): twelve residents with every class among them, and a town that refills toward twelve one newcomer at a time. */
class PopulationTest {
    private val content = engine.content
    private val customers = engine.config.customers

    /** A new run cut down to its first [alive] heroes; the rest are dead. */
    private fun town(alive: Int, seed: Long = 7): GameState {
        val s = engine.newRun(LegacyProfile(), seed)
        val fallen = s.aliveHeroes().drop(alive).map { it.id }.toSet()
        return s.copy(heroes = s.heroes.mapValues { (id, h) -> if (id in fallen) h.copy(fate = HeroFate.DEAD, diedOnDay = 1) else h })
    }

    /** How many of [trials] arrival checks bring a newcomer to a town of [alive]. */
    private fun arrivalsIn(trials: Int, alive: Int, config: BalanceConfig = engine.config): Int {
        val state = town(alive)
        return (1..trials).count { seed ->
            val ctx = ResolutionContext(state, content, config)
            Heroes.arrivals(ctx, Rng(seed.toLong()))
            ctx.aliveHeroes().size - alive == 1
        }
    }

    @Test
    fun everyRunStartsWithAllFiveClasses() {
        val classes = content.classes.map { it.id }
        assertEquals(5, classes.size)
        val firstClass = HashMap<HeroClassId, Int>()
        for (seed in 1L..1000L) {
            val heroes = engine.newRun(LegacyProfile(), seed).aliveHeroes()
            assertEquals(customers.startingHeroes, heroes.size, "seed $seed")
            assertEquals(classes.toSet(), heroes.take(5).map { it.classId }.toSet(), "seed $seed: the first five take one class each")
            firstClass.merge(heroes.first().classId, 1, Int::plus)
        }
        // The order is seeded, not the catalog's: every class opens some runs, none of them most.
        for (c in classes) assertTrue(firstClass.getOrDefault(c, 0) in 150..250, "class ${c.value} is the first hero in ${firstClass[c]} of 1000 runs")
        // A descendant keeps the lineage's class and counts toward the five.
        for (c in classes) {
            val anchor = LineageAnchor(1, "Mira Vance", "Vance", c, 5, "died on day 9", id = "era1-h3")
            val legacy = LegacyProfile(lineages = listOf(anchor), eras = listOf(EraSummary(1, 12, 8, "The forge fell.")))
            for (seed in 1L..100L) {
                val heroes = engine.newRun(legacy, seed).aliveHeroes()
                assertEquals(listOf(c, anchor.id), heroes.first().let { listOf(it.classId, it.lineageId) }, "seed $seed")
                assertEquals(classes.toSet(), heroes.take(5).map { it.classId }.toSet(), "seed $seed with a ${c.value} descendant")
            }
        }
    }

    @Test
    fun arrivalsRefillTowardTheTarget() {
        assertEquals(listOf(12, 9, 16), listOf(customers.populationTarget, customers.minHeroPopulation, customers.maxHeroPopulation))
        val trials = 4000
        // At the target or above it nobody comes; below it, 0.15 per missing resident; below the floor, always.
        for (alive in listOf(12, 11, 10, 9, 8, 5)) {
            val share = arrivalsIn(trials, alive) / trials.toDouble()
            val expected = when {
                alive < customers.minHeroPopulation -> 1.0
                else -> minOf(customers.arrivalChanceMax, customers.arrivalChancePerMissing * (customers.populationTarget - alive))
            }
            assertTrue(kotlin.math.abs(share - expected) < 0.03, "$alive alive: a newcomer on ${"%.3f".format(share)} of days, expected ${"%.2f".format(expected)}")
        }
        // The chance is capped: with no floor, a town of four still gets its newcomer on six days in ten.
        val noFloor = engine.config.copy(customers = customers.copy(minHeroPopulation = 0))
        assertTrue(kotlin.math.abs(arrivalsIn(trials, 4, noFloor) / trials.toDouble() - customers.arrivalChanceMax) < 0.03)
        // One newcomer a day at most, and whole runs stay inside the event cap and come back to the target after losses.
        var days = 0
        var atOrAboveFloor = 0
        var living = 0L
        for (seed in 1L..200L) {
            var before = engine.newRun(LegacyProfile(), seed)
            SimulationDriver(engine, maxDays = 60, onDayResolved = { s, _ ->
                val alive = s.aliveHeroes().size
                assertTrue(alive <= customers.maxHeroPopulation + 1, "seed $seed day ${s.day}: $alive alive")  // a retiree's pupil may arrive at the cap
                days++; living += alive
                if (alive >= customers.minHeroPopulation) atOrAboveFloor++
                before = s
            }).playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR)
            assertTrue(before.day > 1)
        }
        assertTrue(atOrAboveFloor >= days * 0.97, "the town is at nine or more on $atOrAboveFloor of $days days")
        assertTrue(living.toDouble() / days in 10.5..13.5, "mean living heroes ${living.toDouble() / days}")
    }

    @Test
    fun oneHeroesDrawPerDayForArrivals() {
        for (alive in listOf(16, 13, 12, 11, 10, 9, 8, 3)) {
            val state = town(alive.coerceAtMost(12)).let { s ->
                // Towns above the starting twelve: event arrivals, generated outside the stream under test.
                if (alive <= 12) s else ResolutionContext(s, content, engine.config).also { c -> repeat(alive - 12) { c.updateHero(Heroes.generate(c, Rng(1000L + it))) } }.toState()
            }
            for (seed in 1L..50L) {
                val ctx = ResolutionContext(state, content, engine.config)
                val rng = Rng(seed)
                Heroes.arrivals(ctx, rng)
                val arrived = ctx.aliveHeroes().size - alive
                assertTrue(arrived in 0..1)
                // Exactly one draw decides the day, whatever the population; a newcomer is then generated from the same stream.
                val expected = Rng(seed).also { r ->
                    r.nextDouble()
                    if (arrived == 1) Heroes.generate(ResolutionContext(state, content, engine.config), r)
                }
                assertEquals(expected.state, rng.state, "$alive alive, seed $seed, arrived=$arrived")
                if (alive >= customers.populationTarget) assertEquals(0, arrived)
                if (alive < customers.minHeroPopulation) assertEquals(1, arrived)
            }
        }
    }
}
