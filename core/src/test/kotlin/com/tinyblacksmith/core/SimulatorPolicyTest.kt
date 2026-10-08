package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The BALANCED_INVEST purchasing rule (best affordable tier within `gold - reserve`) and the BALANCED_REPUTED pricing rule. */
class SimulatorPolicyTest {
    private val content = LaunchContent.catalog

    private fun firstForgeTiers(reserve: Int): Pair<Int, Int> {
        val (_, state) = SimulationDriver(maxDays = 1, reserve = reserve).playRun(LegacyProfile(), 1, Policy.BALANCED_INVEST)
        val first = state.weapons.values.minBy { it.id.value }
        return content.material(first.coreId).tier to content.material(first.augmentId).tier
    }

    @Test
    fun investBuysTheBestTierWithinGoldMinusReserve() {
        // 250 starting gold: moonsteel (180) then grave dust (55) fit in 250; sun ash (85) does not.
        assertEquals(6 to 4, firstForgeTiers(reserve = 0))
        // Budget 130: starsteel (120) fits; the 10 gold left cannot beat the owned stormglass (tier 3, cost 0).
        assertEquals(5 to 3, firstForgeTiers(reserve = 120))
        // Budget 10: nothing premium fits, so the best owned pair from the starting kit (silver + stormglass) is forged.
        assertEquals(3 to 3, firstForgeTiers(reserve = 240))
    }

    @Test
    fun investWithAnUnreachableReserveMatchesBalancedFair() {
        val fair = SimulationDriver().playRun(LegacyProfile(), 1234, Policy.BALANCED_FAIR).first
        val invest = SimulationDriver(reserve = 1_000_000).playRun(LegacyProfile(), 1234, Policy.BALANCED_INVEST).first
        assertEquals(fair, invest)
    }

    @Test
    fun reputedListsAtTheReputationCeiling() {
        // Known Name L3 starts the run with reputation 15, so the ceiling is +15 % over the fair price.
        val engine = GameEngine()
        val knownName = content.upgrades.first { it.effect == UpgradeEffect.STARTING_REPUTATION }
        val legacy = LegacyProfile(upgrades = mapOf(knownName.id to knownName.maxLevel))
        val startingReputation = engine.newRun(legacy, 7).reputation
        assertTrue(startingReputation > 0)
        val factor = 1.0 + (startingReputation * engine.config.reputationPricePerPoint).coerceIn(0.0, engine.config.reputationPriceCap)
        val (_, state) = SimulationDriver(engine, maxDays = 1).playRun(legacy, 7, Policy.BALANCED_REPUTED)
        val listed = state.listedWeapons()
        assertTrue(listed.isNotEmpty())
        for (w in listed) assertEquals((engine.suggestedPrice(w) * factor).toInt(), w.listedPrice)
    }

    @Test
    fun investIsDeterministicPerSeed() {
        val driver = SimulationDriver()
        val a = driver.playRun(LegacyProfile(), 4321, Policy.BALANCED_INVEST).first
        val b = driver.playRun(LegacyProfile(), 4321, Policy.BALANCED_INVEST).first
        assertEquals(a, b)
    }
}
