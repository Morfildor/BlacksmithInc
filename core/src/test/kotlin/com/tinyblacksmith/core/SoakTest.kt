package com.tinyblacksmith.core

import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GDD 15.3: soak simulations over many thousands of in-game days without integer overflow or unbounded memory.
 * Raids are disabled via [Simulator.forcedSurvival] (siegeModifier 0: raid power 0, forge damage clamps to 0) so the
 * run cannot end; everything else (forging, market, expeditions, deaths, arrivals, blessings, commissions) runs.
 */
class SoakTest {
    private val days = 5000
    private val retentionDays = 30
    private val intCeiling = Int.MAX_VALUE / 4 // far from wrap-around, with headroom for 4x longer runs

    @Test
    fun fiveThousandDaysForcedSurvivalStaysInBounds() {
        var maxGold = 0; var maxHeroGold = 0; var maxXp = 0; var maxHeroFame = 0; var maxWeaponFame = 0; var maxReputation = 0
        var maxEvents = 0; var maxPressure = 0; var maxLevel = 0
        var dayEventsTotal = 0L
        var lastState: GameState? = null
        val maxLevelAllowed = Simulator.forcedSurvival().heroMaxLevel
        val driver = SimulationDriver(
            engine = GameEngine(config = Simulator.forcedSurvival()), maxDays = days,
            eventRetentionDays = retentionDays, maxForgesPerDay = 1,
        ) { s, _ ->
            lastState = s
            maxGold = maxOf(maxGold, s.gold)
            maxReputation = maxOf(maxReputation, s.reputation)
            maxEvents = maxOf(maxEvents, s.events.size)
            dayEventsTotal += s.lastResolution?.events?.size ?: 0
            for (h in s.heroes.values) { maxHeroGold = maxOf(maxHeroGold, h.gold); maxXp = maxOf(maxXp, h.xp); maxHeroFame = maxOf(maxHeroFame, h.fame); maxLevel = maxOf(maxLevel, h.level) }
            for (w in s.weapons.values) maxWeaponFame = maxOf(maxWeaponFame, w.fame)
            for (f in s.factions.values) maxPressure = maxOf(maxPressure, f.pressure)
            // Invariants are asserted by the engine after every command; these catch silent wrap-around.
            assertTrue(s.gold >= 0 && s.reputation >= 0 && s.nextEventSerial > 0 && s.nextWeaponSerial > 0 && s.nextHeroSerial > 0 && s.nextCommissionSerial > 0, "day ${s.day}")
            assertTrue(s.heroes.values.all { it.xp >= 0 && it.fame >= 0 && it.gold >= 0 && it.level in 1..maxLevelAllowed }, "day ${s.day}")
        }
        val started = System.nanoTime()
        val (stats, state) = driver.playRun(LegacyProfile(), 99, Policy.BALANCED_FAIR)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertFalse(stats.ended, "forced survival must not end: ${state.endCause}")
        assertEquals(days, stats.daysSurvived)
        assertEquals(days + 1, state.day)
        assertTrue(state === lastState)

        val serials = listOf(state.nextEventSerial, state.nextWeaponSerial, state.nextHeroSerial, state.nextCommissionSerial)
        for (v in listOf(maxGold, maxHeroGold, maxXp, maxHeroFame, maxWeaponFame, maxReputation) + serials) assertTrue(v in 0..intCeiling, "out of bounds: $v")
        assertTrue(maxPressure in 0..100)
        assertTrue(maxLevel <= driver.engine.config.heroMaxLevel)
        val eventsPerDay = state.nextEventSerial.toDouble() / days
        assertTrue(maxEvents <= retentionDays * 200, "event log must stay bounded under retention: $maxEvents")
        assertTrue(state.events.all { it.day > state.day - retentionDays - 1 })

        println(
            "SOAK $days days in $elapsedMs ms: maxGold=$maxGold maxHeroGold=$maxHeroGold maxXp=$maxXp maxHeroFame=$maxHeroFame maxWeaponFame=$maxWeaponFame " +
                "maxReputation=$maxReputation maxPressure=$maxPressure serials(event/weapon/hero/commission)=$serials " +
                "weapons=${state.weapons.size} heroes=${state.heroes.size} (alive ${state.aliveHeroes().size}) deaths=${stats.heroDeaths} " +
                "events: retained=${state.events.size} max=$maxEvents emitted=${state.nextEventSerial - 1} (${"%.1f".format(eventsPerDay)}/day, untrimmed would be ${state.nextEventSerial - 1}) " +
                "forged=${stats.weaponsForged} sold=${stats.weaponsSold} sieges=${stats.siegesSurvived}/${stats.siegesLost}",
        )
    }

    /** GDD 15.3 PROPOSED: nominal day sim p95 < 200 ms on mid-range Android. Records the JVM number and holds it to the same budget (a JVM that misses it leaves a phone no chance). */
    @Test
    fun endDayP95OverThousandDays() {
        val perf = Simulator.measureEndDay(days = 1000, seed = 1)
        assertEquals(1000, perf.days)
        println("PERF End Day over ${perf.days} days (JVM, forced survival, BALANCED_FAIR): p50=${"%.2f".format(perf.p50Ms)} ms p95=${"%.2f".format(perf.p95Ms)} ms max=${"%.2f".format(perf.maxMs)} ms; state at end: weapons=${perf.weaponsAtEnd} heroes=${perf.heroesAtEnd} events=${perf.eventsAtEnd}")
        assertTrue(perf.p95Ms > 0.0 && perf.p95Ms < 200.0, "End Day p95 ${perf.p95Ms} ms, budget 200 ms")
    }
}
