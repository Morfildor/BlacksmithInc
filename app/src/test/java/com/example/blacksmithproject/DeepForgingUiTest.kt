package com.example.blacksmithproject

import com.example.blacksmithproject.ui.FightNoteKind
import com.example.blacksmithproject.ui.RecipeSlot
import com.example.blacksmithproject.ui.benchRelics
import com.example.blacksmithproject.ui.benchUi
import com.example.blacksmithproject.ui.bladeRules
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.fightNotes
import com.example.blacksmithproject.ui.forgeOptions
import com.example.blacksmithproject.ui.forgeWorkbench
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.crafting.Capabilities
import com.tinyblacksmith.core.crafting.ForgeOutlook
import com.tinyblacksmith.core.crafting.WeaponRules
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.sim.CombatSandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The forge outlook, a metal's rule on the weapon card, the bench and the notebook's fight notes: pure readings of the core's own answers. */
class DeepForgingUiTest {
    private val engine = GameEngine()
    private val content = engine.content
    private val combat = content.combat!!
    private val guild = engine.newRun(LegacyProfile(), 42, charterId = GuildContent.TOWNS_LAST_HOPE)
    private val classic = engine.newRun(LegacyProfile(), 42)
    private val draft = ForgeDraft(familyId = LaunchContent.SWORD, coreId = LaunchContent.IRON, augmentId = LaunchContent.STORMGLASS)

    private fun GameState.with(vararg weapons: Weapon) = copy(weapons = this.weapons + weapons.associateBy { it.id })

    @Test
    fun theOutlookIsOnlyInAGuildRunOnceTheRecipeIsWhole() {
        assertNull(engine.forgeWorkbench(classic, draft, emptyList()).outlook)
        assertNull(engine.forgeWorkbench(guild, draft.copy(augmentId = null), emptyList()).outlook)
        assertNotNull(engine.forgeWorkbench(guild, draft, emptyList()).outlook)
    }

    @Test
    fun theOutlookKeepsWhatIsCertainApartFromWhatARollDecides() {
        val ui = engine.forgeWorkbench(guild, draft, emptyList()).outlook!!
        val core = ForgeOutlook.of(guild, content, engine.config, Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.STORMGLASS, null, draft.risk, null))!!
        assertEquals(core.role, ui.role)
        assertEquals(core.wielders, ui.suits)
        assertEquals(core.certain.rules.map { it.id }, ui.certain.map { it.id })
        assertEquals(listOf("metal", "element"), ui.certain.map { it.source })
        assertEquals(core.possible.map { it.name to it.text }, ui.possible.map { it.name to it.text })
        assertTrue("a balanced quick forge leaves something to the roll", ui.possible.isNotEmpty())
        // Nothing a roll may add is among the rules the recipe is said to have.
        val rolled = core.possible.flatMap { it.rules }.map { it.id }
        assertTrue(rolled.isNotEmpty())
        assertTrue(ui.certain.none { it.id in rolled })
        // Each ingredient says what it is for, and the needs are the core's own words under the rule's name.
        assertEquals(core.parts.filter { it.role.isNotEmpty() }.map { it.name to it.role }, ui.parts.map { it.name to it.role })
        assertEquals(core.missing.map { (id, needs) -> core.certain.rule(id)!!.effect.name to needs.map(Capabilities::text) }, ui.needs.map { it.rule to it.needs })
        assertEquals(core.tradeoff, ui.tradeoff)
        // The recipe for the bench: the middle of what the forge can give, with nothing rolled on it.
        assertEquals((core.qualityLow + core.qualityHigh) / 2, ui.demonstration.quality)
        assertTrue(ui.demonstration.affixes.isEmpty() && ui.demonstration.flaws.isEmpty() && ui.demonstration.signatureId == null)
    }

    @Test
    fun theBellowsCountInTheOutlookOnlyWhenTheForgeWillUseThem() {
        val plain = engine.forgeWorkbench(guild, draft, emptyList()).outlook!!
        val blown = engine.forgeWorkbench(guild, draft, emptyList(), bellows = true).outlook!!
        val core = ForgeOutlook.of(guild, content, engine.config, Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.STORMGLASS, null, draft.risk, null, bellows = true))!!
        assertEquals(core.certain.rules.map { it.id }, blown.certain.map { it.id })
        assertTrue(blown.certain.size >= plain.certain.size)
    }

    @Test
    fun aMetalSaysItsRuleInTheTrayOfAGuildRunOnly() {
        val tray = engine.forgeOptions(guild, draft, RecipeSlot.METAL, null)
        assertTrue(tray.all { it.fight.isNotEmpty() })
        val iron = combat.coreById.getValue(LaunchContent.IRON).effects.single()
        assertEquals(listOf("${iron.name}: ${iron.description}"), tray.first { it.id == LaunchContent.IRON.value }.fight)
        assertTrue(engine.forgeOptions(classic, draft, RecipeSlot.METAL, null).all { it.fight.isEmpty() })
    }

    @Test
    fun aMetalsRuleIsOnTheCardOfAWeaponForgedSinceAndNotOnAnOlderOne() {
        val forgedNow = CombatSandbox.blade("w_new", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.STORMGLASS, craft = WeaponRules.CRAFT)
        val older = forgedNow.copy(craft = 1)
        assertEquals(listOf("Iron holds" to "metal", "Arc" to "element"), bladeRules(forgedNow, content).road.map { it.name to it.source })
        assertEquals(listOf("Arc" to "element"), bladeRules(older, content).road.map { it.name to it.source })
        assertTrue(engine.itemDetail(guild.with(forgedNow), forgedNow.id)!!.rules!!.road.any { it.source == "metal" })
        assertFalse(engine.itemDetail(guild.with(older), older.id)!!.rules!!.road.any { it.source == "metal" })
    }

    @Test
    fun aRuleTwoPartsBringIsListedOnceWithBothAndNotedOnTheCard() {
        val both = CombatSandbox.blade("w_both", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.STORMGLASS, LaunchContent.BINDING_SALT, affixes = listOf(LaunchContent.STORMCHARGED))
        val rules = bladeRules(both, content)
        val burst = rules.road.single { it.name == "Charge burst" }
        assertEquals(1, burst.alsoFrom.size)
        assertTrue(burst.heading, burst.heading.startsWith("Charge burst · ") && burst.heading.endsWith(", also ${burst.alsoFrom.single()}"))
        assertTrue(rules.notes.any { "Charge burst" in it && "counts once" in it })
    }

    @Test
    fun theBenchListsEveryRoadRuleAsFiredOrSilentAndLeavesTheRunAsItWas() {
        val before = guild.copy()
        val weapon = CombatSandbox.blade("w_bench", LaunchContent.SWORD, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, LaunchContent.DRAGON_OIL, craft = WeaponRules.CRAFT)
        val state = guild.with(weapon)
        val stocked = state.copy()
        val road = WeaponRules.of(weapon, content).rules(home = false).map { it.id }
        assertTrue(road.size >= 3)
        for (company in Bench.companions) for (target in Bench.targets) {
            val report = Bench.run(weapon, false, Bench.defaultWielder(weapon.familyId, content), company.id, target.id, content, engine.config, benchRelics(state, content))
            val ui = benchUi(report)
            assertEquals("${company.id} ${target.id}", road.sorted(), (ui.fired.map { it.id } + ui.silent.map { it.id }).sorted())
            assertTrue(ui.fired.all { it.times >= 1 && it.chain.isNotEmpty() } && ui.silent.all { it.why.isNotEmpty() })
            // Everything made up is carried to the sheet, line for line.
            assertEquals(report.synthetic, ui.synthetic)
            assertTrue(ui.synthetic.size >= 4 && ui.synthetic.none { it.startsWith("A recipe") })
            assertEquals(report.result, ui.result)
        }
        assertEquals(stocked, state)
        assertEquals(before, guild)
    }

    @Test
    fun aRecipeOnTheBenchSaysItIsNotAWeaponTheSmithOwns() {
        val recipe = engine.forgeWorkbench(guild, draft, emptyList()).outlook!!.demonstration
        val ui = benchUi(Bench.run(recipe, true, Bench.defaultWielder(recipe.familyId, content), "alone", "armoured", content, engine.config))
        assertTrue(ui.synthetic.any { it.startsWith("A recipe, not a weapon you own") })
        assertFalse(recipe.id in guild.weapons)
    }

    @Test
    fun aRuleOnlyForTheWallIsNamedAsNotTested() {
        val sentient = CombatSandbox.blade("w_s", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, affixes = listOf(LaunchContent.SENTIENT))
        val ui = benchUi(Bench.run(sentient, false, Bench.defaultWielder(sentient.familyId, content), "alone", "armoured", content, engine.config))
        assertEquals(listOf("Sentient (at home)"), ui.notTested)
        assertTrue((ui.fired.map { it.name } + ui.silent.map { it.name }).none { it == "Sentient (at home)" })
    }

    @Test
    fun theWorkshopsPartyRelicsGoToTheBenchAndTheOthersDoNot() {
        assertTrue(benchRelics(guild, content).all { it in combat.relicEffects })
        val party = combat.relicEffects.keys.first()
        val held = guild.copy(relics = guild.relics + com.tinyblacksmith.core.model.ActiveRelic(party) + com.tinyblacksmith.core.model.ActiveRelic(com.tinyblacksmith.core.content.Depth.ASHEN_BELLOWS))
        assertEquals((benchRelics(guild, content) + party).distinct(), benchRelics(held, content).distinct())
    }

    @Test
    fun aForgedRuleIsLearnedItsPathSuggestedAndSeenOnlyOnceAFightHasShownIt() {
        val journal = guild.legacy.journal
        val all = combat.interactions.size
        val blank = fightNotes(journal, combat)
        assertTrue(blank.learned.isEmpty() && blank.paths.isEmpty())
        assertEquals(all, blank.hidden)

        val forged = fightNotes(journal.copy(learnedRules = setOf("core_bronze")), combat)
        assertEquals(listOf("Bronze rings back"), forged.learned.map { it.name })
        val path = forged.paths.single()
        assertEquals("guard_answer", path.id)
        assertEquals(FightNoteKind.SUGGESTED, path.kind)
        assertEquals(listOf("Bronze rings back"), path.known)
        assertEquals(combat.interactions.first { it.id == "guard_answer" }.constraint, path.tradeoff)
        assertEquals(all - 1, forged.hidden)
        assertTrue(forged.hiddenLine!!.startsWith("${all - 1} more to find."))

        val seen = fightNotes(journal.copy(learnedRules = setOf("core_bronze"), witnessed = setOf("guard_answer")), combat)
        assertEquals(FightNoteKind.WITNESSED, seen.paths.single().kind)
        assertEquals(forged.learned, seen.learned)
        // The three kinds never share a word.
        assertEquals(3, FightNoteKind.entries.map { it.word }.toSet().size)
        assertEquals(3, FightNoteKind.entries.map { it.sign }.toSet().size)
    }

    @Test
    fun theFightNotesPageIsForAGuildRunOrAJournalThatAlreadyHoldsSome() {
        assertNotNull(engine.fightNotes(guild))
        assertNull(engine.fightNotes(classic))
        val carried = classic.copy(legacy = classic.legacy.copy(journal = classic.legacy.journal.copy(learnedRules = setOf("core_bronze"))))
        assertEquals(listOf("Bronze rings back"), engine.fightNotes(carried)!!.learned.map { it.name })
    }
}
