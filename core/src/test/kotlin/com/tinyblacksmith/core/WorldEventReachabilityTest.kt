package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C15: the GDD's 25 events are 23 pooled world events ([WorldEvents.all]) and two generational rules (Champion
 * Retirement, Guild Founded; `Heroes.resolveRetirements`). Every one has to be observed in real play, not just fired by
 * hand in a unit test. Half of the runs are new accounts; half are veteran accounts that carry what three earlier eras
 * left behind, because the Legend Board, lineage and era events (`famous_blade`, `descendant`) cannot happen without one.
 */
class WorldEventReachabilityTest {
    private val engine = GameEngine()
    private val perCohort = 1_000
    private val policies = listOf(Policy.BALANCED_ACTIVE, Policy.SYNERGY, Policy.EXPERT_ACTIVE, Policy.BALANCED_FAIR)

    /** Events that exist only for an account with history: reachable on a veteran account, not required on a new one. */
    private val needsAHistory = setOf("famous_blade", "descendant")

    private val rules = mapOf("champion_retirement" to EventType.HERO_RETIRED, "guild_founded" to EventType.GUILD_FOUNDED)

    /** A veteran account: three eras of BALANCED_ACTIVE played and claimed (journal, Legend Board, lineages, eras), no points spent. */
    private fun veteran(account: Int): LegacyProfile {
        var legacy = LegacyProfile()
        val driver = SimulationDriver(engine)
        repeat(3) { era ->
            val state = driver.playRun(legacy, 900_000L + account * 10 + era, Policy.BALANCED_ACTIVE).second
            legacy = (engine.claimLegacy(legacy, engine.closeRun(state)) as LegacyOutcome.Updated).legacy
        }
        return legacy
    }

    /** Runs in which each pooled event id (and each rule) was seen at least once. */
    private fun observe(legacyFor: (Int) -> LegacyProfile, firstSeed: Long): Map<String, Int> {
        val seen = HashMap<String, Int>()
        for (i in 0 until perCohort) {
            val inThisRun = HashSet<String>()
            SimulationDriver(engine, onDayResolved = { s, _ ->
                for (e in s.lastResolution?.events.orEmpty()) {
                    if (e.type == EventType.WORLD_EVENT) e.data["event"]?.let { inThisRun += it }
                    rules.forEach { (name, type) -> if (e.type == type) inThisRun += name }
                }
            }).playRun(legacyFor(i), firstSeed + i, policies[i % policies.size])
            for (id in inThisRun) seen.merge(id, 1, Int::plus)
        }
        return seen
    }

    @Test
    fun everyPooledEventFiresInTwoThousandRunsAndBothRulesTrigger() {
        val pooled = WorldEvents.all.map { it.id }
        assertTrue(pooled.size == 23 && pooled.toSet().size == 23, "the pool must hold 23 distinct events, has ${pooled.size}")

        val veterans = List(8) { veteran(it) }
        assertTrue(veterans.all { it.legendBoard.isNotEmpty() && it.lineages.isNotEmpty() && it.eras.isNotEmpty() }, "the veteran accounts must carry history")
        val fresh = observe({ LegacyProfile() }, 1L)
        val old = observe({ veterans[it % veterans.size] }, 100_000L)

        val table = (pooled + rules.keys).joinToString("\n") { id -> "  %-22s new account %4d of $perCohort runs, veteran account %4d".format(id, fresh[id] ?: 0, old[id] ?: 0) }
        println("World events seen in $perCohort + $perCohort runs (policies ${policies.joinToString { it.name }}):\n$table")

        // An event that comes as a morning visitor in the launch catalogue no longer fires by itself; its reachability is the visitor's (EncountersTest, the simulator's depth table).
        val asVisitors = pooled.filter { Encounters.replaces(LaunchContent.catalog, it) }
        assertEquals(setOf("collector", "wandering_master", "merchant_festival"), asVisitors.toSet())
        val unreachable = (pooled - asVisitors.toSet() + rules.keys).filter { id ->
            val seenWhereItMust = if (id in needsAHistory) old[id] ?: 0 else fresh[id] ?: 0
            seenWhereItMust == 0
        }
        assertTrue(
            unreachable.isEmpty(),
            "UNREACHABLE in simulation (a finding, not a test to loosen): $unreachable\n$table",
        )
    }
}
