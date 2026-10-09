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
        assertEquals(11, catalog.upgrades.size, "upgrade tracks: the eight of v2 plus catalog access, recipe odds and legacy artifacts (GDD 9)")
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

    /** Plan 4.4: the counts are the launch catalog's; the rules are `validate()`'s, shown here to hold and, one by one, to bite. */
    @Test
    fun namePoolsObeyTheAuthoringRules() {
        assertEquals(120, catalog.firstNames.size)
        assertEquals(96, catalog.surnames.size)
        assertEquals(emptyList(), catalog.validate())
        assertEquals(emptyList(), SliceContent.catalog.validate())
        assertTrue(catalog.firstNames.maxOf { it.length } + 1 + catalog.surnames.maxOf { it.length } <= 20)

        fun problems(first: List<String> = catalog.firstNames, sur: List<String> = catalog.surnames) = catalog.copy(firstNames = first, surnames = sur).validate()
        fun firstName(name: String, problem: String) = assertTrue(problems(first = catalog.firstNames + name).any { problem in it }, "$name: expected '$problem' in ${problems(first = catalog.firstNames + name)}")
        fun surname(name: String, problem: String) = assertTrue(problems(sur = catalog.surnames + name).any { problem in it }, "$name: expected '$problem' in ${problems(sur = catalog.surnames + name)}")
        // Rule 1: plain letters, one capital, 3-8 and 4-11.
        for (bad in listOf("Jo", "Maximilian", "Jean-Luc", "Renée", "ODell", "wren")) firstName(bad, "First name $bad must be")
        for (bad in listOf("Lux", "Brackenridge", "O'Marrow", "Van Hoek")) surname(bad, "Surname $bad must be")
        // Rule 3: distinct at a glance.
        firstName("Mirabel", "First names Mira and Mirabel share their first three letters")
        firstName("Nessa", "First names Tessa and Nessa are too alike")       // one edit, different initials
        firstName("Myrna", "First names Mira and Myrna are too alike")        // two edits, the same initial
        surname("Vancent", "Surnames Vance and Vancent share their first four letters")
        surname("Mallow", "Surnames Tallow and Mallow are too alike")
        assertTrue(problems(first = catalog.firstNames + listOf("Bolko", "Bystra")).any { "first names begin with B" in it })
        assertTrue(problems(first = catalog.firstNames.filter { it.first() <= 'N' }).any { "initials (at least 15)" in it })
        // Rule 4: no "Rook Rooksbane".
        surname("Wrenfield", "First name Wren and surname Wrenfield share their first four letters")
        // Rule 5: at most four per ending.
        surname("Elmwood", "5 surnames end in -ood")
        // Rule 6: no game terms or stems. The names that left the pool with content 3 are the ones the rules turn away.
        surname("Ashwood", "'ash'"); surname("Mossgrave", "'grave'"); surname("Rooksbane", "'bane'"); surname("Holloway", "'hollowbound'")
        firstName("Ember", "'ember'"); firstName("Sunna", "'sun'"); surname("Granger", "'ranger'")
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

    /** Every string reachable from the catalog (names, descriptions, flavour, name pools), by reflection. */
    private fun strings(value: Any?, out: MutableList<String>) {
        when (value) {
            null -> {}
            is String -> out += value
            is Map<*, *> -> value.forEach { (k, v) -> strings(k, out); strings(v, out) }
            is Iterable<*> -> value.forEach { strings(it, out) }
            is Pair<*, *> -> { strings(value.first, out); strings(value.second, out) }
            else -> if (value.javaClass.name.startsWith("com.tinyblacksmith.core.") && !value.javaClass.isEnum) {
                value.javaClass.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic }
                    .forEach { it.isAccessible = true; strings(it.get(value), out) }
            }
        }
    }

    @Test
    fun noPlayerFacingTextContainsAPercentChance() {
        for (c in listOf(catalog, SliceContent.catalog)) {
            val all = mutableListOf<String>()
            strings(c, all)
            assertTrue(all.size > 300, "the scan reached the catalog's text (${all.size} strings)")
            val offenders = all.filter { '%' in it || it.contains("percent", ignoreCase = true) }
            assertEquals(emptyList(), offenders, "player-facing text states a percent")
        }
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
