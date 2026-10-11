package com.tinyblacksmith.core

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.sim.RarityRow
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Balance v2 rarity shape on launch content (docs/DECISIONS.md): tiers 1-2 common/uncommon, 3-4 rare, 5-6 epic, legendary rare. */
class RarityShapeTest {
    private val rows = Simulator.rarityTable(LaunchContent.catalog, forges = 2000, seed = 1)

    private fun cell(core: String, augment: String, risk: Risk = Risk.BALANCED): RarityRow =
        rows.single { it.coreId == core && it.augmentId == augment && it.risk == risk }

    private fun RarityRow.share(vararg r: Rarity) = r.sumOf { rarity.getValue(it) }

    @Test
    fun ironAndEmberIsCommonOrUncommonAndNeverLegendary() {
        val c = cell("iron", "ember_resin")
        assertTrue(c.share(Rarity.COMMON, Rarity.UNCOMMON) > 0.5, "iron+ember common/uncommon ${c.render()}")
        assertTrue(c.rarity.getValue(Rarity.LEGENDARY) < 0.01, "iron+ember legendary ${c.render()}")
    }

    @Test
    fun silverAndStormglassIsRareCentred() {
        val c = cell("silver", "stormglass")
        assertEquals(Rarity.RARE, c.rarity.maxBy { it.value }.key, c.render())
    }

    @Test
    fun moonsteelAndSunAshIsEpicCentredWithBoundedLegendary() {
        val c = cell("moonsteel", "sun_ash")
        assertTrue(c.share(Rarity.EPIC, Rarity.LEGENDARY) > 0.5, "moonsteel+sun ash epic+legendary ${c.render()}")
        assertTrue(c.rarity.getValue(Rarity.LEGENDARY) <= 0.30, "moonsteel+sun ash legendary ${c.render()}")
        assertEquals(Rarity.EPIC, c.rarity.maxBy { it.value }.key, c.render())
    }

    @Test
    fun meanQualityRisesWithCoreTierAndRisk() {
        val byTier = rows.filter { it.risk == Risk.BALANCED }.groupBy { it.coreTier }.mapValues { (_, r) -> r.map { it.meanQuality }.average() }
        for (tier in 1..5) assertTrue(byTier.getValue(tier) < byTier.getValue(tier + 1), "tier $tier ${byTier[tier]} vs ${byTier[tier + 1]}")
        // Rules 6: no temper forges a Legendary weapon from quality alone, and only a fine metal holds Epic work.
        assertTrue(rows.all { it.rarity.getValue(Rarity.LEGENDARY) == 0.0 })
        val fineTier = BalanceConfig.DEFAULT.depth.legend.fineCoreTier
        val (fine, lesser) = rows.partition { it.coreTier >= fineTier }
        assertTrue(lesser.isNotEmpty() && lesser.all { it.rarity.getValue(Rarity.EPIC) == 0.0 }, "a lesser metal stops at Rare")
        assertEquals(setOf("starsteel", "moonsteel"), fine.map { it.coreId }.toSet())
        assertTrue(fine.all { it.rarity.getValue(Rarity.EPIC) > 0.0 }, "every fine recipe reaches Epic at every temper")
        // A bolder temper widens the roll. A fine recipe that sits under the Epic floor reaches Epic more often with it,
        // and one that sits above the floor keeps Epic more often in a safe hand.
        var under = 0
        var above = 0
        for ((recipe, r) in fine.groupBy { it.coreId to it.augmentId }) {
            val safe = r.single { it.risk == Risk.SAFE }.rarity.getValue(Rarity.EPIC)
            val reckless = r.single { it.risk == Risk.RECKLESS }.rarity.getValue(Rarity.EPIC)
            if (safe < 0.5) { under++; assertTrue(reckless > safe, "$recipe under the floor: safe $safe, reckless $reckless") }
            else { above++; assertTrue(safe > reckless, "$recipe above the floor: safe $safe, reckless $reckless") }
        }
        assertTrue(under >= 3 && above >= 3, "both kinds of fine recipe are in the table: $under under, $above above")
    }
}
