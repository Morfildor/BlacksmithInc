package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LaunchContentTest {
    private val catalog = LaunchContent.catalog

    @Test
    fun lockedCountsAndValidity() {
        assertEquals(emptyList(), catalog.validate())
        assertEquals(6, catalog.families.size)
        assertEquals(16, catalog.materials.size)
        assertEquals(6, catalog.materials(MaterialCategory.CORE).size)
        assertEquals(6, catalog.materials(MaterialCategory.AUGMENT).size)
        assertEquals(4, catalog.materials(MaterialCategory.CATALYST).size)
        assertEquals(5, catalog.classes.size)
        assertEquals(3, catalog.factions.size)
        assertEquals(12, catalog.affixes.count { it.kind == AffixKind.BENEFICIAL })
        assertEquals(6, catalog.affixes.count { it.kind == AffixKind.FLAW })
        assertEquals(8, catalog.blessings.size)
        assertTrue(catalog.upgrades.size in 6..8, "upgrade tracks: ${catalog.upgrades.size}")
        assertTrue(catalog.traits.size >= 7)
        assertEquals((1..6).toList(), catalog.materials(MaterialCategory.CORE).map { it.tier })
    }

    @Test
    fun everyAugmentHasABeneficialAffixOfItsElement() {
        for (augment in catalog.materials(MaterialCategory.AUGMENT)) {
            assertTrue(
                catalog.affixes.any { it.kind == AffixKind.BENEFICIAL && it.element == augment.element },
                "no beneficial ${augment.element} affix for ${augment.name}",
            )
        }
    }

    @Test
    fun everyFamilyFitsEveryClass() {
        for (family in catalog.families) for (cls in catalog.classes) {
            assertTrue(cls.id in family.classFit, "${family.name} has no classFit for ${cls.name}")
        }
    }

    @Test
    fun affinityTablesAreCompleteAndBounded() {
        val cores = catalog.materials(MaterialCategory.CORE)
        val augments = catalog.materials(MaterialCategory.AUGMENT)
        for (c in cores) for (a in augments) assertTrue((c.id to a.id) in catalog.coreAugmentAffinity, "missing ${c.name}+${a.name}")
        for (a in augments) for (f in catalog.families) assertTrue((a.id to f.id) in catalog.augmentFamilyAffinity, "missing ${a.name} on ${f.name}")
        assertEquals(36, catalog.coreAugmentAffinity.size)
        assertEquals(36, catalog.augmentFamilyAffinity.size)
        (catalog.coreAugmentAffinity.values + catalog.augmentFamilyAffinity.values).forEach { assertTrue(it in -4..8, "affinity $it out of range") }
        // Journal.describeAffinity reads >= 7 as "excellent affinity".
        for (a in augments) assertTrue(cores.any { (catalog.coreAugmentAffinity[it.id to a.id] ?: 0) >= 7 }, "${a.name} has no excellent core partner")
    }

    @Test
    fun sliceIdsAndAffinitiesSurviveUnchanged() {
        val slice = SliceContent.catalog
        slice.materials.forEach { assertTrue(it.id in catalog.materialById, "slice material ${it.id.value} missing") }
        slice.affixes.forEach { assertTrue(it.id in catalog.affixById, "slice affix ${it.id.value} missing") }
        slice.upgrades.forEach { assertTrue(it.id in catalog.upgradeById, "slice upgrade ${it.id.value} missing") }
        slice.blessings.forEach { assertTrue(it.id in catalog.blessingById, "slice blessing ${it.id.value} missing") }
        slice.coreAugmentAffinity.forEach { (k, v) -> assertEquals(v, catalog.coreAugmentAffinity[k], "affinity ${k.first.value}+${k.second.value} changed") }
        slice.augmentFamilyAffinity.forEach { (k, v) -> assertEquals(v, catalog.augmentFamilyAffinity[k], "affinity ${k.first.value} on ${k.second.value} changed") }
    }

    @Test
    fun fiftySeededHeadlessRunsComplete() {
        val driver = SimulationDriver(GameEngine(content = catalog))
        for (seed in 1L..50L) {
            val (stats, state) = driver.playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR)
            assertTrue(stats.daysSurvived >= 1, "seed $seed survived ${stats.daysSurvived} days")
            assertTrue(state.isEnded || state.day > driver.maxDays, "seed $seed neither ended nor hit the day cap")
        }
    }
}
