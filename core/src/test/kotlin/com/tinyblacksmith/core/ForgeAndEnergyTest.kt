package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ForgeAndEnergyTest {
    private fun fresh(seed: Long = 1) = engine.newRun(LegacyProfile(), seed).withMaterials()

    @Test
    fun everyValidAttemptYieldsUsableWeaponWithinBounds() {
        var legendary = 0
        for (seed in 1L..300L) {
            var s = fresh(seed)
            for (risk in Risk.entries) {
                val out = s.forgeAccepted(quickSword(risk, SliceContent.SILVER, SliceContent.STORMGLASS))
                val w = out.state.weapon(out.forgedWeaponId!!)
                assertTrue(w.quality in 1..100, "quality ${w.quality}")
                assertTrue(w.power >= 1)
                assertEquals(WeaponLocation.Storage, w.location)
                assertEquals(w.rarity, com.tinyblacksmith.core.crafting.Forge.rarityFor(w.quality, engine.config))
                if (w.rarity == Rarity.LEGENDARY) legendary++
                s = out.state
            }
        }
        assertTrue(legendary > 0, "silver+stormglass reckless forging should sometimes be legendary")
    }

    @Test
    fun defectRatesTrackRiskProfiles() {
        val counts = Risk.entries.associateWith { intArrayOf(0, 0) }
        for (seed in 1L..400L) {
            var s = fresh(seed)
            for (risk in Risk.entries) {
                val out = s.forgeAccepted(quickSword(risk))
                val e = out.events.first { it.type == EventType.WEAPON_FORGED }
                if (e.data["defect"] == "true") counts.getValue(risk)[0]++
                if (e.data["exceptional"] == "true") counts.getValue(risk)[1]++
                s = out.state
            }
        }
        val n = 400.0
        fun near(actual: Int, expected: Double) = assertTrue(kotlin.math.abs(actual / n - expected) < 0.05, "observed ${actual / n} expected $expected")
        near(counts.getValue(Risk.SAFE)[0], 0.02); near(counts.getValue(Risk.RECKLESS)[0], 0.20)
        near(counts.getValue(Risk.SAFE)[1], 0.06); near(counts.getValue(Risk.RECKLESS)[1], 0.30)
    }

    @Test
    fun defectAlwaysAttachesAFlawAndStillProducesWeapon() {
        var flawed = 0
        for (seed in 1L..200L) {
            val out = fresh(seed).forgeAccepted(quickSword(Risk.RECKLESS))
            val w = out.state.weapon(out.forgedWeaponId!!)
            val e = out.events.first { it.type == EventType.WEAPON_FORGED }
            if (e.data["defect"] == "true") { flawed++; assertEquals(1, w.flaws.size); assertTrue(w.power >= 1) }
        }
        assertTrue(flawed > 10)
    }

    @Test
    fun materialsAndEnergyConsumedExactlyOnce() {
        val s = fresh()
        val out = s.forgeAccepted(quickSword())
        assertEquals(s.energy - 2, out.state.energy)
        assertEquals(s.materials.getValue(SliceContent.IRON) - 1, out.state.materials.getValue(SliceContent.IRON))
        assertEquals(s.materials.getValue(SliceContent.EMBER_RESIN) - 1, out.state.materials.getValue(SliceContent.EMBER_RESIN))
        assertEquals(1, out.state.weapons.size)
    }

    @Test
    fun missingMaterialIsRejectedWithoutSideEffects() {
        val s = engine.newRun(LegacyProfile(), 3).copy(materials = emptyMap())
        val out = engine.handle(s, quickSword())
        assertIs<CommandOutcome.Rejected>(out)
        assertIs<GameError.MissingMaterial>(out.error)
    }

    @Test
    fun advancedForgeCostsFourAndAllowsCatalystQuickDoesNot() {
        val s = fresh()
        val adv = Command.Forge(ForgeMode.ADVANCED, SliceContent.SWORD, SliceContent.IRON, SliceContent.EMBER_RESIN, SliceContent.BINDING_SALT, Risk.SAFE)
        val out = s.forgeAccepted(adv)
        assertEquals(s.energy - 4, out.state.energy)
        assertEquals(s.materials.getValue(SliceContent.BINDING_SALT) - 1, out.state.materials.getValue(SliceContent.BINDING_SALT))
        val quickWithCatalyst = engine.handle(s, adv.copy(mode = ForgeMode.QUICK))
        assertIs<CommandOutcome.Rejected>(quickWithCatalyst)
        assertIs<GameError.CatalystRequiresAdvanced>(quickWithCatalyst.error)
    }

    @Test
    fun tenEnergyOverworkFourAndNextDayExhaustion() {
        var s = fresh()
        assertEquals(10, s.energy)
        repeat(5) { s = s.forgeAccepted(quickSword()).state }
        assertEquals(0, s.energy)
        assertEquals(0, s.overworkToday)
        s = s.forgeAccepted(quickSword()).state // overwork 2
        assertEquals(2, s.overworkToday)
        s = s.forgeAccepted(quickSword()).state // overwork 4
        assertEquals(4, s.overworkToday)
        val rejected = engine.handle(s, quickSword())
        assertIs<CommandOutcome.Rejected>(rejected)
        assertIs<GameError.NotEnoughEnergy>(rejected.error)
        s = s.endDay()
        assertEquals(6, s.energy, "next day capacity reduced by overwork")
        assertEquals(0, s.overworkToday)
        s = s.endDay()
        assertEquals(10, s.energy, "debt repaid naturally")
    }

    @Test
    fun journalMovesObservedThenUnderstoodAndDoesNotFarm() {
        var s = fresh()
        val key = Journal.coreAugmentKey(SliceContent.IRON, SliceContent.EMBER_RESIN)
        s = s.forgeAccepted(quickSword()).state
        assertEquals(KnowledgeState.OBSERVED, s.legacy.journal.state(key))
        s = s.forgeAccepted(quickSword()).state
        s = s.forgeAccepted(quickSword()).state
        assertEquals(KnowledgeState.UNDERSTOOD, s.legacy.journal.state(key))
        val discoveries = s.discoveriesThisRun
        s = s.forgeAccepted(quickSword()).state
        assertEquals(discoveries, s.discoveriesThisRun, "repeating a known experiment grants nothing")
        assertNotNull(s.events.firstOrNull { it.type == EventType.DISCOVERY })
    }

    @Test
    fun forgeMasteryUpgradeRaisesQuality() {
        val plain = engine.newRun(LegacyProfile(), 11).withMaterials()
        val mastered = engine.newRun(LegacyProfile(upgrades = mapOf(SliceContent.UPG_MASTERY to 3)), 11).withMaterials()
        val a = plain.forgeAccepted(quickSword(Risk.SAFE)).let { it.state.weapon(it.forgedWeaponId!!) }
        val b = mastered.forgeAccepted(quickSword(Risk.SAFE)).let { it.state.weapon(it.forgedWeaponId!!) }
        assertEquals(a.quality + 12, b.quality)
    }
}
