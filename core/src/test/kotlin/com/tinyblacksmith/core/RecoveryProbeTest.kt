package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.RecoveryProbe
import com.tinyblacksmith.core.sim.RunRecovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** G10: stuck (no sale, no legal forge), drought (no sale, a forge) and streaks, on states built by hand. */
class RecoveryProbeTest {
    private val probe = RecoveryProbe(engine)
    private val start = engine.newRun(LegacyProfile(), 1)

    @Test
    fun noMaterialsNoGoldAndNothingToSellIsStuckAndAForgeMakesItADrought() {
        val broke = start.copy(materials = emptyMap(), gold = 0)
        assertFalse(probe.canForge(broke))
        assertFalse(probe.canSell(broke))
        assertTrue(probe.canForge(start.copy(materials = emptyMap(), gold = 1_000)), "gold buys a core and an augment")
        assertTrue(probe.canForge(start.copy(gold = 0)), "the starting kit forges without gold")
        assertFalse(probe.canForge(broke.copy(materials = start.materials, energy = 0, overworkToday = engine.config.maxOverworkPerDay)), "no energy, no overwork left")
    }

    @Test
    fun aBladeAHeroCanAffordAndGainFromIsAPossibleSale() {
        val out = start.withMaterials().forgeAccepted(quickSword())
        val rich = out.state.let { s -> s.copy(heroes = s.heroes.mapValues { (_, h) -> h.copy(gold = 100_000) }) }
        assertTrue(probe.canSell(rich), "stored blade, rich heroes")
        val poor = rich.copy(heroes = rich.heroes.mapValues { (_, h) -> h.copy(gold = 0) })
        assertFalse(probe.canSell(poor), "nobody can pay the going rate")
    }

    @Test
    fun streaksCountConsecutiveStuckMorningsAndAnyOtherMorningEndsThem() {
        val stuck = start.copy(materials = emptyMap(), gold = 0)
        val drought = start
        val p = RecoveryProbe(engine)
        for (s in listOf(stuck, stuck, stuck, drought, stuck, stuck)) p.morning(s)
        assertEquals(RunRecovery(days = 6, stuckDays = 5, droughtDays = 1, longestStuckStreak = 3), p.finish())
    }
}
