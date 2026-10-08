package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.ActiveBlessing
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The launch-content blessing/upgrade effects added in P6 must change outcomes, not just exist as data. */
class LaunchEffectsTest {
    private val engine = GameEngine(content = LaunchContent.catalog)
    private val forge = Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)

    private fun exceptionalRate(legacy: LegacyProfile, blessing: Boolean, seeds: Int = 300): Double {
        var exceptional = 0
        for (seed in 1..seeds) {
            var s = engine.newRun(legacy, seed.toLong())
            s = s.copy(materials = engine.content.materials.associate { it.id to 50 })
            if (blessing) s = s.copy(blessings = listOf(ActiveBlessing(LaunchContent.LUCKY_ALLOY, expiresDay = 99)))
            val out = engine.handle(s, forge) as CommandOutcome.Accepted
            if (out.events.any { it.data["exceptional"] == "true" }) exceptional++
        }
        return exceptional.toDouble() / seeds
    }

    @Test
    fun luckyAlloyAndLuckyHammerRaiseExceptionalRate() {
        val base = exceptionalRate(LegacyProfile(), blessing = false)
        val blessed = exceptionalRate(LegacyProfile(), blessing = true)
        val hammer = exceptionalRate(LegacyProfile(upgrades = mapOf(LaunchContent.UPG_LUCK to 3)), blessing = false)
        assertTrue(blessed > base + 0.03, "Lucky Alloy: base $base blessed $blessed")
        assertTrue(hammer > base + 0.02, "Lucky Hammer: base $base hammer $hammer")
    }

    @Test
    fun thriftyHandsSometimesSparesTheAugmentAndNeverTheCore() {
        val legacy = LegacyProfile(upgrades = mapOf(LaunchContent.UPG_EFFICIENCY to 3))
        var saved = 0
        for (seed in 1..200) {
            val s = engine.newRun(legacy, seed.toLong()).copy(materials = engine.content.materials.associate { it.id to 5 })
            val out = engine.handle(s, forge) as CommandOutcome.Accepted
            assertEquals(4, out.state.materials[LaunchContent.IRON])
            val augment = out.state.materials[LaunchContent.EMBER_RESIN]!!
            assertTrue(augment in 4..5)
            if (augment == 5) saved++
        }
        assertTrue(saved in 30..90, "augment saved $saved/200 at 30%")
        assertEquals(0, (1..50).count { seed ->
            val s = engine.newRun(LegacyProfile(), seed.toLong()).copy(materials = engine.content.materials.associate { it.id to 5 })
            (engine.handle(s, forge) as CommandOutcome.Accepted).state.materials[LaunchContent.EMBER_RESIN] == 5
        })
    }

    @Test
    fun wellStockedCellarAddsToEveryStartingKitMaterial() {
        val plain = engine.newRun(LegacyProfile(), 7L).materials
        val stocked = engine.newRun(LegacyProfile(upgrades = mapOf(LaunchContent.UPG_STOCK to 2)), 7L).materials
        assertTrue(plain.isNotEmpty())
        plain.forEach { (id, n) -> assertEquals(n + 4, stocked[id], id.value) }
    }

    @Test
    fun runicInsightDoublesJournalProgress() {
        val s = engine.newRun(LegacyProfile(), 3L).copy(
            materials = engine.content.materials.associate { it.id to 50 },
            blessings = listOf(ActiveBlessing(LaunchContent.RUNIC_INSIGHT, expiresDay = 99)),
        )
        val out = engine.handle(s, forge) as CommandOutcome.Accepted
        val key = com.tinyblacksmith.core.model.Journal.coreAugmentKey(LaunchContent.IRON, LaunchContent.EMBER_RESIN)
        assertEquals(2, out.state.legacy.journal.experiments[key])
    }

    @Test
    fun heroTastesOnlyUseElementsTheCatalogCanForge() {
        val sliceElements = engine.content.materials.mapNotNull { it.element }.toSet()
        val slice = GameEngine()
        val sliceSet = slice.content.materials.mapNotNull { it.element }.toSet()
        for (seed in 1..40) {
            slice.newRun(LegacyProfile(), seed.toLong()).heroes.values.forEach { h ->
                h.elementTaste?.let { assertTrue(it in sliceSet, "slice hero wants $it") }
            }
            engine.newRun(LegacyProfile(), seed.toLong()).heroes.values.forEach { h ->
                h.elementTaste?.let { assertTrue(it in sliceElements) }
            }
        }
    }
}
