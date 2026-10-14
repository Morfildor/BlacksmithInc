package com.tinyblacksmith.core

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.legacy.Legacy
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyPreviewTest {
    private val catalog = LaunchContent.catalog
    private val config = BalanceConfig.DEFAULT
    private val tracks = config.legacyTracks

    /** What the engine adds up (GameEngine.upgradeTotal) for an effect at a level, worked out here from the catalog. */
    private fun total(effect: UpgradeEffect, level: Int) = catalog.upgrades.filter { it.effect == effect }.sumOf { it.magnitudePerLevel * level }

    /** Tracks that change a hidden chance: their sentence words it (decision A), so it holds no number but a level it compares with. */
    private val chanceTracks = setOf(UpgradeEffect.MATERIAL_EFFICIENCY, UpgradeEffect.EXCEPTIONAL_CHANCE, UpgradeEffect.RECIPE_ODDS)

    @Test
    fun everyUpgradeLevelHasAConcretePreview() {
        for (def in catalog.upgrades) {
            var last: String? = null
            for (level in 1..def.maxLevel) {
                val p = Legacy.preview(def.id, level)!!
                assertTrue(p.text.isNotBlank(), "${def.name} level $level")
                assertTrue(p.text.startsWith("Next era. "), p.text)
                if (def.effect in chanceTracks) {
                    // Words, not odds: no number but the level it compares with, no count, no promise.
                    val numbers = Regex("\\d+").findAll(p.text).map { it.value }.toList()
                    assertEquals(if (level == 1) emptyList() else listOf("${level - 1}"), numbers, p.text)
                    assertTrue(" in 10" !in p.text && "It is never certain." in p.text, p.text)
                } else {
                    assertTrue("${p.before}" in p.text && "${p.after}" in p.text, "${def.name} level $level shows its before and after: ${p.text}")
                }
                assertNotEquals(p.before, p.after, p.text)
                assertNotEquals(last, p.text, "${def.name} level $level differs from the level before")
                assertTrue('%' !in p.text, p.text)
                last = p.text
                // The numbers are the engine's.
                val (before, after) = when (def.effect) {
                    UpgradeEffect.STARTING_ENERGY -> config.baseDailyEnergy + total(def.effect, level - 1) to config.baseDailyEnergy + total(def.effect, level)
                    UpgradeEffect.STARTING_GOLD -> config.startingGold + total(def.effect, level - 1) to config.startingGold + total(def.effect, level)
                    UpgradeEffect.STARTING_INTEGRITY -> config.startingForgeIntegrity + total(def.effect, level - 1) to config.startingForgeIntegrity + total(def.effect, level)
                    UpgradeEffect.QUALITY_BONUS, UpgradeEffect.STARTING_MATERIALS, UpgradeEffect.STARTING_REPUTATION -> total(def.effect, level - 1) to total(def.effect, level)
                    UpgradeEffect.MATERIAL_EFFICIENCY, UpgradeEffect.EXCEPTIONAL_CHANCE -> total(def.effect, level - 1) to total(def.effect, level)
                    UpgradeEffect.CATALOG_ACCESS -> total(def.effect, level - 1) * tracks.catalogStockPerLevel to total(def.effect, level) * tracks.catalogStockPerLevel
                    UpgradeEffect.RECIPE_ODDS -> (total(def.effect, level - 1) * tracks.recipeOddsPerLevel * 100).roundToInt() to (total(def.effect, level) * tracks.recipeOddsPerLevel * 100).roundToInt()
                    UpgradeEffect.LEGACY_ARTIFACTS -> {
                        fun strength(steps: Int) = (100 * minOf(tracks.returnedLegendQualityFactorMax, config.returnedLegendQualityFactor + steps * tracks.legendQualityFactorPerLevel)).roundToInt()
                        strength(total(def.effect, level - 1)) to strength(total(def.effect, level))
                    }
                }
                assertEquals(before to after, p.before to p.after, "${def.name} level $level: ${p.text}")
            }
        }
    }

    @Test
    fun numbersFollowTheConfigAndLevelsOutsideTheTrackHaveNoPreview() {
        val energy = catalog.upgrades.first { it.effect == UpgradeEffect.STARTING_ENERGY }
        assertEquals("Next era. Start with 11 energy, up from 10.", Legacy.preview(energy.id, 1)!!.text)
        assertEquals("Next era. Start with 14 energy, up from 13.", Legacy.preview(energy.id, 3, config = config.copy(baseDailyEnergy = 11))!!.text)
        val stock = catalog.upgrades.first { it.effect == UpgradeEffect.CATALOG_ACCESS }
        assertEquals(6, Legacy.preview(stock.id, 3, config = config.copy(legacyTracks = tracks.copy(catalogStockPerLevel = 2)))!!.after)
        val thrifty = catalog.upgrades.first { it.effect == UpgradeEffect.MATERIAL_EFFICIENCY }
        assertEquals("Next era. The augment is kept now and then after a forge, more often than without it. It is never certain.", Legacy.preview(thrifty.id, 1)!!.text)
        assertEquals("Next era. The augment is kept often after a forge, more often than at level 2. It is never certain.", Legacy.preview(thrifty.id, 3)!!.text)
        assertNull(Legacy.preview(energy.id, 0))
        assertNull(Legacy.preview(energy.id, energy.maxLevel + 1))
        // The slice catalog has shorter tracks; the same function serves it.
        val sliceEnergy = SliceContent.catalog.upgrades.first { it.effect == UpgradeEffect.STARTING_ENERGY }
        assertNull(Legacy.preview(sliceEnergy.id, 3, SliceContent.catalog))
    }
}
