package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.crafting.Journal as JournalRules
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.crafting.SignatureDef
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignatureAndTechniqueTest {
    private val stormsong = SignatureCatalog.byId.getValue("stormsong")
    private val exact = Command.Forge(ForgeMode.ADVANCED, SliceContent.BOW, SliceContent.SILVER, SliceContent.STORMGLASS, SliceContent.BINDING_SALT, Risk.SAFE)

    private fun fresh(seed: Long, legacy: LegacyProfile = LegacyProfile()) = engine.newRun(legacy, seed).withMaterials().copy(energy = 100)

    /** Launch engine with the signature roll forced, so reachability depends only on the recipe, conditions and quality floor. */
    private val launch = GameEngine(content = LaunchContent.catalog, config = engine.config.copy(signatureBaseChance = 1.0, signatureMaxChance = 1.0))

    private fun freshLaunch(seed: Long, e: GameEngine = launch) =
        e.newRun(LegacyProfile(), seed).copy(materials = LaunchContent.catalog.materials.associate { it.id to 50 }, energy = 100)

    private fun exactCommand(def: SignatureDef) =
        Command.Forge(ForgeMode.ADVANCED, def.familyId, def.coreId, def.augmentId, def.catalystId, def.risk ?: Risk.BALANCED)

    @Test
    fun catalogIsConsistentWithLaunchContent() {
        val content = LaunchContent.catalog
        assertEquals(24, SignatureCatalog.all.size)
        assertEquals(SignatureCatalog.all.size, SignatureCatalog.all.map { it.id }.toSet().size, "ids unique")
        assertEquals(SignatureCatalog.all.size, SignatureCatalog.all.map { it.name }.toSet().size, "names unique")
        assertEquals(SignatureCatalog.all.size, SignatureCatalog.all.map { Triple(it.familyId, it.coreId, it.augmentId) }.toSet().size, "one signature per recipe")
        for (f in content.families) assertEquals(4, SignatureCatalog.all.count { it.familyId == f.id }, "4 per family (${f.name})")
        for (s in SignatureCatalog.all) {
            assertEquals(MaterialCategory.CORE, content.material(s.coreId).category, s.id)
            assertEquals(MaterialCategory.AUGMENT, content.material(s.augmentId).category, s.id)
            s.catalystId?.let { assertEquals(MaterialCategory.CATALYST, content.material(it).category, s.id) }
            s.grantedAffixes.forEach { content.affix(it) }
            assertTrue(s.minQuality in 1..100 && s.bonusPower > 0, s.id)
            assertEquals(s, SignatureCatalog.forRecipe(exactCommand(s)), "recipe lookup")
        }
        assertEquals(content.materials(MaterialCategory.CORE).map { it.id }.toSet(), SignatureCatalog.all.map { it.coreId }.toSet(), "every core appears")
        val elements = SignatureCatalog.all.map { content.material(it.augmentId).element }.toSet()
        assertEquals(Element.entries.toSet(), elements, "every element appears")
        assertEquals(content.materials(MaterialCategory.CATALYST).map { it.id }.toSet(), SignatureCatalog.all.mapNotNull { it.catalystId }.toSet(), "every catalyst appears")
        assertTrue(SignatureCatalog.all.count { it.catalystId == null } in 4..8, "a few recipes need no catalyst")
    }

    @Test
    fun everyLaunchSignatureIsReachableAndNoneFiresWithoutItsConditions() {
        for (def in SignatureCatalog.all) {
            val exactCmd = exactCommand(def)
            var transforms = 0
            for (seed in 1L..60L) {
                val out = launch.handle(freshLaunch(seed), exactCmd) as CommandOutcome.Accepted
                val w = out.state.weapon(out.forgedWeaponId!!)
                if (w.signatureId == null) {
                    assertTrue(w.quality < def.minQuality, "${def.id} seed $seed: eligible forge did not transform at quality ${w.quality}")
                    continue
                }
                transforms++
                assertEquals(def.id, w.signatureId)
                assertEquals(def.name, w.name)
                assertTrue(w.quality >= def.minQuality)
                assertTrue(w.affixes.containsAll(def.grantedAffixes), "${def.id} granted affixes present: ${w.affixes}")
                assertEquals(KnowledgeState.SIGNATURE_DISCOVERED, out.state.legacy.journal.state(def.journalKey))
                assertEquals(1, out.events.count { it.type == EventType.SIGNATURE_DISCOVERED })
            }
            assertTrue(transforms > 0, "${def.id} never transformed in 60 seeds with a forced roll")

            // Wrong or missing catalyst, wrong risk: never, whatever the roll.
            val wrong = mutableListOf<Command.Forge>()
            if (def.catalystId != null) {
                wrong += exactCmd.copy(catalystId = null)
                wrong += exactCmd.copy(catalystId = LaunchContent.catalog.materials(MaterialCategory.CATALYST).first { it.id != def.catalystId }.id)
            }
            if (def.risk != null) Risk.entries.filter { it != def.risk }.forEach { wrong += exactCmd.copy(risk = it) }
            for (cmd in wrong) for (seed in 1L..20L) {
                val out = launch.handle(freshLaunch(seed), cmd) as CommandOutcome.Accepted
                val w = out.state.weapon(out.forgedWeaponId!!)
                assertNull(w.signatureId, "${def.id} fired with $cmd")
                assertFalse(w.name == def.name)
                assertTrue(out.events.none { it.type == EventType.SIGNATURE_DISCOVERED })
            }
            // Below the quality floor: never. The first attempt earns the first rung of the ladder; the second, right in all but quality, the rung that asks for finer work.
            val poor = GameEngine(content = LaunchContent.catalog, config = launch.config.copy(qualityBase = -100))
            val out = poor.handle(freshLaunch(1, poor), exactCmd) as CommandOutcome.Accepted
            assertNull(out.state.weapon(out.forgedWeaponId!!).signatureId, def.id)
            assertEquals(KnowledgeState.OBSERVED, out.state.legacy.journal.state(def.journalKey))
            val clue = out.events.single { it.type == EventType.DISCOVERY && it.data["key"] == def.journalKey }
            assertTrue(clue.text.contains("hides something more"), clue.text)
            val second = (poor.handle(out.state, exactCmd) as CommandOutcome.Accepted).events.single { it.type == EventType.DISCOVERY && it.data["key"] == def.journalKey }
            assertTrue(second.text.contains("finer work"), second.text)
        }
    }

    @Test
    fun launchSignatureForgesAreDeterministic() {
        for (def in SignatureCatalog.all) {
            val cmd = exactCommand(def)
            val a = launch.handle(freshLaunch(11), cmd) as CommandOutcome.Accepted
            val b = launch.handle(freshLaunch(11), cmd) as CommandOutcome.Accepted
            assertEquals(a.state, b.state, def.id)
            assertEquals(a.events, b.events, def.id)
        }
    }

    @Test
    fun exactRecipeDiscoversSignatureAcrossSeedsAndWrongCombinationsNever() {
        var discovered = 0
        for (seed in 1L..200L) {
            val out = fresh(seed).forgeAccepted(exact)
            val w = out.state.weapon(out.forgedWeaponId!!)
            if (w.signatureId == null) continue
            discovered++
            assertEquals(stormsong.id, w.signatureId)
            assertEquals(stormsong.name, w.name)
            assertTrue(w.quality >= stormsong.minQuality)
            assertTrue(w.affixes.containsAll(stormsong.grantedAffixes), "granted affixes present: ${w.affixes}")
            val e = out.events.single { it.type == EventType.SIGNATURE_DISCOVERED }
            assertEquals(6, e.priority)
            assertEquals(KnowledgeState.SIGNATURE_DISCOVERED, out.state.legacy.journal.state(stormsong.journalKey))
            assertEquals(1, out.state.discoveriesThisRun)
        }
        assertTrue(discovered in 10..80, "expected roughly 15 % of eligible forges to transform, got $discovered/200")

        val noCatalyst = exact.copy(catalystId = null)
        val unlisted = Command.Forge(ForgeMode.ADVANCED, SliceContent.SWORD, SliceContent.IRON, SliceContent.FROST_BLOOM, SliceContent.BINDING_SALT, Risk.RECKLESS)
        assertNull(SignatureCatalog.forRecipe(unlisted))
        for (seed in 1L..200L) {
            for (cmd in listOf(noCatalyst, unlisted)) {
                val out = fresh(seed).forgeAccepted(cmd)
                val w = out.state.weapon(out.forgedWeaponId!!)
                assertNull(w.signatureId)
                assertFalse(w.name == stormsong.name)
                assertTrue(out.events.none { it.type == EventType.SIGNATURE_DISCOVERED })
                assertFalse(out.state.legacy.journal.state(stormsong.journalKey) == KnowledgeState.SIGNATURE_DISCOVERED)
            }
        }
    }

    @Test
    fun discoveryCountsOnceAndSurvivesRunEnd() {
        // Pre-understood affinities so discoveriesThisRun moves only for the signature.
        val known = Journal(interactions = mapOf(
            Journal.coreAugmentKey(SliceContent.SILVER, SliceContent.STORMGLASS) to KnowledgeState.UNDERSTOOD,
            Journal.augmentFamilyKey(SliceContent.STORMGLASS, SliceContent.BOW) to KnowledgeState.UNDERSTOOD,
        ))
        var s: GameState? = null
        for (seed in 1L..50L) {
            var candidate = fresh(seed, LegacyProfile(journal = known))
            var transforms = 0
            repeat(24) {
                val out = candidate.forgeAccepted(exact)
                candidate = out.state
                if (out.events.any { it.type == EventType.SIGNATURE_DISCOVERED }) transforms++
            }
            if (transforms >= 2) { s = candidate; break }
        }
        val state = s ?: error("no seed produced two transformations in 24 forges")
        assertEquals(1, state.discoveriesThisRun, "only the first transformation is a discovery")
        assertEquals(1, state.events.count { it.type == EventType.SIGNATURE_DISCOVERED && it.data["first"] == "true" })
        assertTrue(state.events.count { it.type == EventType.SIGNATURE_DISCOVERED } >= 2)
        assertEquals(KnowledgeState.SIGNATURE_DISCOVERED, state.legacy.journal.state(stormsong.journalKey))

        var ending = state.copy(town = state.town.copy(integrity = 1))
        while (!ending.isEnded) ending = ending.endDayAccepted().state
        val end = engine.closeRun(ending)
        val legacy = (engine.claimLegacy(LegacyProfile(), end) as LegacyOutcome.Updated).legacy
        assertEquals(KnowledgeState.SIGNATURE_DISCOVERED, legacy.journal.state(stormsong.journalKey))
        val hint = JournalRules.hint(legacy.journal, engine.content, stormsong.journalKey)
        assertTrue(hint.contains(stormsong.name) && !hint.contains('%'), hint)
    }

    @Test
    fun nearMissRecordsADescriptiveClueEachTime() {
        val s0 = fresh(7)
        val out = s0.forgeAccepted(Command.Forge(ForgeMode.QUICK, SliceContent.BOW, SliceContent.SILVER, SliceContent.STORMGLASS, null, Risk.SAFE))
        assertEquals(KnowledgeState.OBSERVED, out.state.legacy.journal.state(stormsong.journalKey))
        val clue = out.events.single { it.type == EventType.DISCOVERY && it.data["key"] == stormsong.journalKey }
        assertTrue(clue.text.contains("Stormglass sang against the Silver") && clue.text.contains("hides something more"), clue.text)
        assertEquals(s0.discoveriesThisRun, out.state.discoveriesThisRun, "a clue is not a discovery")
        val hint = JournalRules.hint(out.state.legacy.journal, engine.content, stormsong.journalKey)
        assertTrue(hint != "Unknown" && !hint.contains('%') && !hint.contains(stormsong.name), hint)

        val again = out.state.forgeAccepted(Command.Forge(ForgeMode.QUICK, SliceContent.BOW, SliceContent.SILVER, SliceContent.STORMGLASS, null, Risk.SAFE))
        // The journal keeps answering (G07): the second miss says what kind of catalyst, in that catalyst's own words. `ClueLadderTest` has the rest.
        val second = again.events.single { it.type == EventType.DISCOVERY && it.data["key"] == stormsong.journalKey }
        assertTrue(second.text.contains("wants something to bind it") && !second.text.contains('%'), second.text)
        assertEquals(KnowledgeState.OBSERVED, again.state.legacy.journal.state(stormsong.journalKey))
        assertEquals(s0.discoveriesThisRun, again.state.discoveriesThisRun, "nor is the second")
    }

    @Test
    fun techniquesRequireAdvancedForge() {
        val s = fresh(3)
        for (t in Technique.entries) {
            val quick = engine.handle(s, Command.Forge(ForgeMode.QUICK, SliceContent.SWORD, SliceContent.IRON, SliceContent.EMBER_RESIN, null, Risk.BALANCED, t))
            assertIs<CommandOutcome.Rejected>(quick)
            assertEquals(GameError.TechniqueRequiresAdvanced(t), quick.error)
            assertIs<CommandOutcome.Accepted>(engine.handle(s, Command.Forge(ForgeMode.ADVANCED, SliceContent.SWORD, SliceContent.IRON, SliceContent.EMBER_RESIN, null, Risk.BALANCED, t)))
        }
    }

    @Test
    fun temperLowersDefectRateOver400Seeds() {
        val plainCmd = Command.Forge(ForgeMode.ADVANCED, SliceContent.SWORD, SliceContent.IRON, SliceContent.EMBER_RESIN, null, Risk.RECKLESS)
        var plainDefects = 0
        var temperedDefects = 0
        for (seed in 1L..400L) {
            val s = fresh(seed)
            val plain = s.forgeAccepted(plainCmd).events.first { it.type == EventType.WEAPON_FORGED }.data["defect"] == "true"
            val tempered = s.forgeAccepted(plainCmd.copy(technique = Technique.TEMPER)).events.first { it.type == EventType.WEAPON_FORGED }.data["defect"] == "true"
            if (plain) plainDefects++
            if (tempered) { temperedDefects++; assertTrue(plain, "tempering never introduces a defect the plain forge avoided") }
        }
        assertTrue(temperedDefects < plainDefects - 10, "tempered $temperedDefects vs plain $plainDefects")
    }

    @Test
    fun quenchForcesElementAffixAndEtchAddsSlot() {
        val base = Command.Forge(ForgeMode.ADVANCED, SliceContent.SWORD, SliceContent.IRON, SliceContent.EMBER_RESIN, null, Risk.SAFE)
        var plainWithoutElement = 0
        var etchGained = 0
        for (seed in 1L..100L) {
            val s = fresh(seed)
            val plain = s.forgeAccepted(base).let { it.state.weapon(it.forgedWeaponId!!) }
            val quenched = s.forgeAccepted(base.copy(technique = Technique.QUENCH)).let { it.state.weapon(it.forgedWeaponId!!) }
            val etched = s.forgeAccepted(base.copy(technique = Technique.ETCH)).let { it.state.weapon(it.forgedWeaponId!!) }
            assertTrue(SliceContent.FLAMING in quenched.affixes, "quench guarantees the element affix")
            assertEquals(plain.quality - engine.config.quenchQualityPenalty, quenched.quality)
            if (SliceContent.FLAMING !in plain.affixes) plainWithoutElement++
            if (etched.affixes.size > plain.affixes.size) etchGained++
        }
        assertTrue(plainWithoutElement > 0, "safe iron swords sometimes roll no affix slot, so quench is a real choice")
        assertTrue(etchGained > 50, "etch adds an affix slot ($etchGained/100)")
    }
}
