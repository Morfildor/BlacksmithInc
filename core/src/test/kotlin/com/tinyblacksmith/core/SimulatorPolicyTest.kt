package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals

/** The BALANCED_INVEST purchasing rule: best affordable tier within `gold - reserve`, cheapest fallback otherwise. */
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
    fun investIsDeterministicPerSeed() {
        val driver = SimulationDriver()
        val a = driver.playRun(LegacyProfile(), 4321, Policy.BALANCED_INVEST).first
        val b = driver.playRun(LegacyProfile(), 4321, Policy.BALANCED_INVEST).first
        assertEquals(a, b)
    }
}
