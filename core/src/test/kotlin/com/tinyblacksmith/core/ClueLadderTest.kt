package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.ClueRung
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.crafting.SignatureDef
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.tinyblacksmith.core.crafting.Journal as JournalRules

/** G07, E3: a signature's four-rung clue ladder, one phrase per catalyst, rumours from real events, clues kept across eras. */
class ClueLadderTest {
    private val content = LaunchContent.catalog
    /** The signature roll never succeeds here: every forge at a base recipe is a miss, whatever it got right. */
    private val never = GameEngine(content, BalanceConfig.DEFAULT.copy(signatureBaseChance = 0.0, signatureChancePerMastery = 0.0, signatureMaxChance = 0.0))
    private val always = GameEngine(content, BalanceConfig.DEFAULT.copy(signatureBaseChance = 1.0, signatureMaxChance = 1.0))
    private val config = never.config
    private val sunlance = SignatureCatalog.byId.getValue("sunlance")        // dragon oil, reckless, 75
    private val winterwake = SignatureCatalog.byId.getValue("winterwake")    // no catalyst, safe, 60

    private fun fresh(seed: Long = 7, e: GameEngine = never, legacy: LegacyProfile = LegacyProfile()) =
        e.newRun(legacy, seed).copy(materials = content.materials.associate { it.id to 50 }, energy = 1000)

    private fun base(def: SignatureDef, catalyst: Boolean = false, risk: Risk = Risk.BALANCED) =
        Command.Forge(if (catalyst) ForgeMode.ADVANCED else ForgeMode.QUICK, def.familyId, def.coreId, def.augmentId, def.catalystId.takeIf { catalyst }, risk)

    private fun rungs(s: GameState, def: SignatureDef) = JournalRules.rungs(s.legacy.journal, def)
    private fun hint(s: GameState, def: SignatureDef) = JournalRules.hint(s.legacy.journal, content, def.journalKey)
    private fun GameState.forge(cmd: Command.Forge, e: GameEngine = never) = (e.handle(this, cmd) as com.tinyblacksmith.core.engine.CommandOutcome.Accepted)

    @Test
    fun aSecondMissEarnsTheCatalystRung() {
        var s = fresh()
        assertEquals(emptySet(), rungs(s, sunlance))
        assertEquals("Unknown", hint(s, sunlance))
        // First miss: the base recipe, and only that, whatever was wrong with the attempt.
        val first = s.forge(base(sunlance))
        s = first.state
        assertEquals(setOf(ClueRung.RECIPE), rungs(s, sunlance))
        assertEquals("A signature recipe is hidden here.", hint(s, sunlance))
        assertTrue(first.events.single { it.data["key"] == sunlance.journalKey }.text.endsWith("This combination can make a signature weapon."))
        // Second miss without the catalyst: the catalyst rung, in the catalyst's own words.
        val second = s.forge(base(sunlance))
        s = second.state
        assertEquals(setOf(ClueRung.RECIPE, ClueRung.CATALYST), rungs(s, sunlance))
        assertEquals("A signature recipe is hidden here. This recipe needs a catalyst from dragon fire.", hint(s, sunlance))
        assertEquals(ClueRung.CATALYST.name, second.events.single { it.data["key"] == sunlance.journalKey }.data["rung"])
        // With the oil but the wrong temper: the temper rung. Then, right in everything it knows, the last rung.
        s = s.forge(base(sunlance, catalyst = true)).state
        assertEquals("A signature recipe is hidden here. This recipe needs a catalyst from dragon fire. Use Reckless forging.", hint(s, sunlance))
        s = s.forge(base(sunlance, catalyst = true, risk = Risk.RECKLESS)).state
        assertEquals(ClueRung.entries.toSet(), rungs(s, sunlance))
        assertTrue(hint(s, sunlance).endsWith("Reach superb quality or better."), hint(s, sunlance))
        // The ladder is complete: a further miss says nothing new, and nothing above was a discovery.
        val more = s.forge(base(sunlance, catalyst = true, risk = Risk.RECKLESS))
        assertTrue(more.events.none { it.type == EventType.DISCOVERY && it.data["key"] == sunlance.journalKey })
        assertEquals(KnowledgeState.OBSERVED, more.state.legacy.journal.state(sunlance.journalKey))
        assertEquals(0, more.state.events.count { it.type == EventType.SIGNATURE_DISCOVERED })

        // A rung is earned for what the attempt missed, not in a fixed order: the right catalyst and the wrong temper skips to the temper.
        var t = fresh().forge(base(sunlance, catalyst = true)).state
        t = t.forge(base(sunlance, catalyst = true)).state
        assertEquals(setOf(ClueRung.RECIPE, ClueRung.TEMPER), rungs(t, sunlance))
        // A recipe that takes no catalyst says so when its turn comes.
        var w = fresh()
        repeat(4) { w = w.forge(base(winterwake, risk = Risk.SAFE)).state }
        assertEquals("A signature recipe is hidden here. This recipe needs no specific catalyst. Use Safe forging. Reach fine quality or better.", hint(w, winterwake))
    }

    @Test
    fun eachCatalystHasItsOwnPhrase() {
        val catalysts = content.materials(MaterialCategory.CATALYST)
        val phrases = catalysts.associate { it.id to JournalRules.catalystPhrase(it.id) }
        assertEquals(4, catalysts.size)
        assertEquals(catalysts.size, phrases.values.toSet().size, "one phrase each: $phrases")
        val generic = setOf(JournalRules.catalystPhrase(null), JournalRules.catalystPhrase(MaterialId("no_such_catalyst")))
        assertTrue(phrases.values.none { it in generic }, "and none is the fallback or the no-catalyst phrase: $phrases")
        assertEquals("needs a catalyst from dragon fire", phrases.getValue(LaunchContent.DRAGON_OIL))
        assertEquals("needs no specific catalyst", JournalRules.catalystPhrase(null))
        // Every signature that needs a catalyst says so with that catalyst's phrase, and the catalyst's own description echoes it honestly.
        for (sig in SignatureCatalog.all) assertTrue(JournalRules.catalystPhrase(sig.catalystId) in JournalRules.clue(sig, ClueRung.CATALYST, config), sig.id)
        for (c in catalysts) assertTrue(c.flavor.startsWith("Helps the forge along.") && "affix" !in c.flavor, "${c.name}: ${c.flavor}")
        assertFalse(JournalRules.CATALYST_EFFECT.any { it.isDigit() || it == '%' })
    }

    @Test
    fun noOddsAtAnyRung() {
        for (sig in SignatureCatalog.all) for (bits in 0..ClueRung.ALL) {
            for (state in listOf(KnowledgeState.UNKNOWN, KnowledgeState.OBSERVED, KnowledgeState.SIGNATURE_DISCOVERED)) {
                val journal = Journal(interactions = if (state == KnowledgeState.UNKNOWN) emptyMap() else mapOf(sig.journalKey to state), signatureClues = if (bits == 0) emptyMap() else mapOf(sig.journalKey to bits))
                val text = JournalRules.hint(journal, content, sig.journalKey)
                assertFalse(text.any { it.isDigit() } || "%" in text || "chance" in text.lowercase() || "odds" in text.lowercase(), "${sig.id} $bits $state: $text")
                // Only the rungs earned are shown: a clause appears exactly when its rung is held.
                val have = JournalRules.rungs(journal, sig)
                if (state != KnowledgeState.SIGNATURE_DISCOVERED) for (r in ClueRung.entries - ClueRung.RECIPE) {
                    assertEquals(r in have && ClueRung.RECIPE in have, JournalRules.clue(sig, r, config).replaceFirstChar { it.uppercase() } in text, "${sig.id} $bits $state $r: $text")
                }
                if (state == KnowledgeState.SIGNATURE_DISCOVERED) assertTrue(sig.name in text) else assertFalse(sig.name in text, text)
            }
            for (r in ClueRung.entries) assertFalse(JournalRules.clue(sig, r, config).any { it.isDigit() || it == '%' })
        }
        // A profile from before the ladder: "observed" with no bits is the first rung, no more.
        val old = Journal(interactions = mapOf(sunlance.journalKey to KnowledgeState.OBSERVED))
        assertEquals(setOf(ClueRung.RECIPE), JournalRules.rungs(old, sunlance))
        assertEquals("A signature recipe is hidden here.", JournalRules.hint(old, content, sunlance.journalKey))
    }

    @Test
    fun cluesMergeAcrossEras() {
        // Era 1 learns the base recipe and the catalyst; era 2, starting from the claimed profile, learns the temper.
        var one = fresh()
        repeat(2) { one = one.forge(base(sunlance)).state }
        one = one.copy(town = one.town.copy(integrity = 1))
        while (!one.isEnded) one = (never.handle(one, Command.EndDay(TestSupport.endDayId(one))) as com.tinyblacksmith.core.engine.CommandOutcome.Accepted).state
        val afterOne = (never.claimLegacy(LegacyProfile(), never.closeRun(one)) as LegacyOutcome.Updated).legacy
        assertEquals(setOf(ClueRung.RECIPE, ClueRung.CATALYST), JournalRules.rungs(afterOne.journal, sunlance))

        var two = fresh(8, legacy = afterOne)
        assertEquals("A signature recipe is hidden here. This recipe needs a catalyst from dragon fire.", hint(two, sunlance), "the new era opens knowing what the last one learned")
        two = two.forge(base(sunlance, catalyst = true)).state
        two = two.copy(town = two.town.copy(integrity = 1))
        while (!two.isEnded) two = (never.handle(two, Command.EndDay(TestSupport.endDayId(two))) as com.tinyblacksmith.core.engine.CommandOutcome.Accepted).state
        val afterTwo = (never.claimLegacy(afterOne, never.closeRun(two)) as LegacyOutcome.Updated).legacy
        assertEquals(setOf(ClueRung.RECIPE, ClueRung.CATALYST, ClueRung.TEMPER), JournalRules.rungs(afterTwo.journal, sunlance))
        // A rung is never lost by a merge, whichever side holds it; the save keeps the bits.
        val poorer = afterTwo.copy(journal = afterTwo.journal.copy(signatureClues = mapOf(sunlance.journalKey to ClueRung.QUALITY.bit)))
        val merged = (never.claimLegacy(poorer, never.closeRun(two).copy(runId = RunId("another"))) as LegacyOutcome.Updated).legacy
        assertEquals(ClueRung.entries.toSet(), JournalRules.rungs(merged.journal, sunlance))
        assertEquals(merged.journal, com.tinyblacksmith.core.persistence.SaveCodec.decodeLegacy(com.tinyblacksmith.core.persistence.SaveCodec.encodeLegacy(merged)).journal)
    }

    @Test
    fun noSignatureIsFirstFoundWithoutRungOne() {
        // Found on the very first forge: the whole ladder is written with the discovery, the first rung included.
        val found = fresh(e = always).forge(SignatureCatalog.recipe(winterwake).copy(risk = Risk.SAFE), always)
        if (found.state.legacy.journal.state(winterwake.journalKey) == KnowledgeState.SIGNATURE_DISCOVERED) {
            assertEquals(ClueRung.entries.toSet(), rungs(found.state, winterwake))
        }
        // Across bots that hunt signatures and bots that stumble on them: every signature in a journal has its first rung.
        var firsts = 0
        for (policy in listOf(Policy.SIGNATURE_PURSUIT, Policy.BALANCED_FAIR, Policy.SYNERGY)) for (seed in 1L..40L) {
            val (stats, state) = SimulationDriver().playRun(LegacyProfile(), seed, policy)
            val journal = state.legacy.journal
            for ((key, k) in journal.interactions) if (key.startsWith("sig:") && k == KnowledgeState.SIGNATURE_DISCOVERED) {
                firsts++
                assertEquals(ClueRung.ALL, journal.signatureClues[key], "$policy seed $seed $key")
            }
            for ((key, bits) in journal.signatureClues) assertTrue(bits and ClueRung.RECIPE.bit != 0, "$policy seed $seed $key: no rung without the first")
            assertEquals(stats.signatureFirsts, journal.interactions.count { it.key.startsWith("sig:") && it.value == KnowledgeState.SIGNATURE_DISCOVERED })
        }
        assertTrue(firsts > 0, "the runs found signatures")
        // "Use this recipe" is the recipe that qualifies.
        for (sig in SignatureCatalog.all) {
            val cmd = SignatureCatalog.recipe(sig)
            assertNull(sig.missing(cmd, sig.minQuality), sig.id)
            assertEquals(sig, SignatureCatalog.forRecipe(cmd))
        }
        assertEquals(LaunchContent.IRON to LaunchContent.EMBER_RESIN, JournalRules.coreAugmentOf(Journal.coreAugmentKey(LaunchContent.IRON, LaunchContent.EMBER_RESIN)))
        assertNull(JournalRules.coreAugmentOf(sunlance.journalKey))
    }

    @Test
    fun aRumourEarnsOneRungForARealEventWithinItsLimits() {
        val s = fresh(e = TestSupport.engine)
        val cfg = s.let { TestSupport.engine.config.customers }
        val ctx = ResolutionContext(s, content, TestSupport.engine.config)
        val before = ctx.rng(com.tinyblacksmith.core.rng.RngStream.EVENTS).state
        assertTrue(WorldEvents.rumour(ctx, "Mira Vance, back from the kill,", HeroId("h1")))
        val told = ctx.newEvents.single()
        assertEquals(listOf(EventType.DISCOVERY, "true", "h1"), listOf(told.type, told.data["rumour"], told.data["hero"]))
        assertTrue(told.text.startsWith("Mira Vance, back from the kill, shared a clue about ") && told.text.endsWith("This combination can make a signature weapon.") && told.text.none { it == '%' }, told.text)
        val sig = SignatureCatalog.all.single { "sig:${it.id}" == told.data["key"] }
        assertEquals(setOf(ClueRung.RECIPE), JournalRules.rungs(ctx.legacy.journal, sig))
        assertTrue(ctx.rng(com.tinyblacksmith.core.rng.RngStream.EVENTS).state != before, "one pick on the EVENTS stream")
        // Not again within the cooldown; again after it; never past the run's cap; never with the cap at 0.
        assertFalse(WorldEvents.rumour(ctx, "x"))
        ctx.day += cfg.rumourCooldownDays
        assertTrue(WorldEvents.rumour(ctx, "x"))
        repeat(20) { ctx.day += cfg.rumourCooldownDays; WorldEvents.rumour(ctx, "x") }
        assertEquals(cfg.maxRumoursPerRun, ctx.eventCounters[WorldEvents.RUMOUR])
        assertEquals(cfg.maxRumoursPerRun, ctx.newEvents.size)
        assertTrue(TestSupport.engine.let { com.tinyblacksmith.core.engine.Invariants.check(ctx.toState(), it.config, it.shelfSlots(ctx.toState())) }.isEmpty())
        val off = ResolutionContext(s, content, TestSupport.engine.config.let { it.copy(customers = it.customers.copy(maxRumoursPerRun = 0)) })
        assertFalse(WorldEvents.rumour(off, "x"))
        // In play they come from kills and commissions: every rumour of forty runs sits on a day with one of the two.
        var rumours = 0
        for (seed in 1L..40L) {
            val (stats, state) = SimulationDriver().playRun(LegacyProfile(), seed, Policy.EXPERT)
            assertTrue(stats.rumours <= cfg.maxRumoursPerRun)
            rumours += stats.rumours
            assertNotNull(state)
        }
        assertTrue(rumours > 40, "rumours are told ($rumours in 40 runs)")
    }
}
