package com.example.blacksmithproject

import com.example.blacksmithproject.ui.FactTone
import com.example.blacksmithproject.ui.ForgeLearningUi
import com.example.blacksmithproject.ui.LearnedUi
import com.example.blacksmithproject.ui.LegendUi
import com.example.blacksmithproject.ui.RARITY_RULE
import com.example.blacksmithproject.ui.TipAction
import com.example.blacksmithproject.ui.TipTone
import com.example.blacksmithproject.ui.TipUi
import com.example.blacksmithproject.ui.TownSiegeUi
import com.example.blacksmithproject.ui.benchFiredLines
import com.example.blacksmithproject.ui.benchSilentLines
import com.example.blacksmithproject.ui.benchSyntheticTip
import com.example.blacksmithproject.ui.benchUi
import com.example.blacksmithproject.ui.besiegerTip
import com.example.blacksmithproject.ui.detail.Property
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.fightNotes
import com.example.blacksmithproject.ui.learningLine
import com.example.blacksmithproject.ui.learningTip
import com.example.blacksmithproject.ui.legendTip
import com.example.blacksmithproject.ui.mergedStories
import com.tinyblacksmith.core.gazette.GazetteDigest
import com.example.blacksmithproject.ui.oddsTone
import com.example.blacksmithproject.ui.resultLead
import com.example.blacksmithproject.ui.resultTip
import com.example.blacksmithproject.ui.runPointsTip
import com.example.blacksmithproject.ui.siegeTraitLines
import com.example.blacksmithproject.ui.siegeTraitTip
import com.example.blacksmithproject.ui.statuses
import com.example.blacksmithproject.ui.upgradeTip
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.crafting.Interactions
import com.tinyblacksmith.core.crafting.WeaponRules
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.legacy.RunEndResult
import com.tinyblacksmith.core.model.FactionId
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.RunId
import com.tinyblacksmith.core.sim.CombatSandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The V3 cards of the result and record surfaces: every line from a field the screen already holds, the right sign on each, nothing empty. */
class ResultsInspectTest {
    private val engine = GameEngine()
    private val content = engine.content
    private val config = engine.config
    private val forged = engine.handle(engine.newRun(LegacyProfile(), 42L), Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)).stateOrThrow()
    private val blade = forged.weapons.values.single()

    /** No card says nothing: a name, a kind, and no line or section title left blank. */
    private fun assertWhole(tip: TipUi) {
        assertTrue("a card has a name", tip.name.isNotBlank())
        assertTrue("${tip.name} has a kind", tip.kind.isNotBlank())
        tip.sections.forEach { s ->
            s.title?.let { assertTrue("${tip.name}: a section title is blank", it.isNotBlank()) }
            s.lines.forEach { l -> assertTrue("${tip.name}: a blank line", l.text.isNotBlank() || !l.title.isNullOrBlank()) }
        }
        tip.stats.forEach { assertTrue("${tip.name}: a blank stat", it.label.isNotBlank() && it.value.isNotBlank()) }
    }

    @Test
    fun aFreshResultLeadsWithPowerAndQualityAndNamesEachPropertyWithItsSign() {
        val d = engine.itemDetail(forged, blade.id)!!
        val lead = resultLead(d)
        // A full condition on a new weapon and a renown it never had wait on the card.
        assertEquals(if (blade.condition == 100) listOf("Power", "Quality") else listOf("Power", "Quality", "Condition"), lead.stats.map { it.label })
        assertEquals(blade.power, lead.stats.single { it.label == "Power" }.value)
        assertEquals(blade.quality, lead.stats.single { it.label == "Quality" }.value)
        val mixed = d.copy(affixes = listOf(Property("Flaming", "x")), flaws = listOf(Property("Brittle", "y")))
        assertEquals(listOf("Flaming" to FactTone.GOOD, "Brittle" to FactTone.WARN), resultLead(mixed).properties.map { it.value to it.tone })
        // A worn weapon keeps its condition on the lead.
        val worn = forged.copy(weapons = mapOf(blade.id to blade.copy(condition = 40)))
        assertTrue(resultLead(engine.itemDetail(worn, blade.id)!!).stats.any { it.label == "Condition" && it.value == 40 })
    }

    @Test
    fun aGuildResultNamesTheEffectsItFightsByAndAClassicOneNone() {
        val guild = engine.newRun(LegacyProfile(), 42, charterId = GuildContent.TOWNS_LAST_HOPE)
        val weapon = CombatSandbox.blade("w_g", LaunchContent.SWORD, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, LaunchContent.DRAGON_OIL, craft = WeaponRules.CRAFT)
        val d = engine.itemDetail(guild.copy(weapons = guild.weapons + (weapon.id to weapon)), weapon.id)!!
        val rules = d.rules!!
        assertTrue(resultLead(d).rules.isNotEmpty())
        // A metal's or an element's effect is named after its source, so it comes with what it does. One named as a
        // property is said once, as the property.
        val named = (d.affixes + d.flaws).map { it.name }.toSet()
        assertEquals((rules.road + rules.homeOnly).map { com.tinyblacksmith.core.content.CombatContent.label(it.id, it.name) }.filterNot { it in named }, resultLead(d).rules)
        assertTrue(resultLead(d).rules.toString(), "Frost · Chills on hit" in resultLead(d).rules && "Dragon oil" in resultLead(d).rules)
        assertTrue(resultLead(engine.itemDetail(forged, blade.id)!!).rules.isEmpty())
    }

    /** "Flaming" as a property and as an effect of the same name is one line on the result: the property's, with its sign. */
    @Test
    fun anEffectNamedAsAPropertyIsSaidOnceOnTheResult() {
        val guild = engine.newRun(LegacyProfile(), 42, charterId = GuildContent.TOWNS_LAST_HOPE)
        val weapon = CombatSandbox.blade("w_g", LaunchContent.SWORD, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, LaunchContent.DRAGON_OIL, craft = WeaponRules.CRAFT)
        val d = engine.itemDetail(guild.copy(weapons = guild.weapons + (weapon.id to weapon)), weapon.id)!!
        val all = resultLead(d.copy(affixes = emptyList(), flaws = emptyList())).rules
        assertTrue(all.size >= 2)
        val twin = all.first()
        val lead = resultLead(d.copy(affixes = listOf(Property(twin, "x")), flaws = emptyList()))
        assertEquals(listOf(twin to FactTone.GOOD), lead.properties.map { it.value to it.tone })
        assertEquals(all.drop(1), lead.rules)
        // Its card still lists the effect: only the result's lead says it once.
        assertTrue(d.rules!!.let { r -> (r.road + r.homeOnly).any { com.tinyblacksmith.core.content.CombatContent.label(it.id, it.name) == twin } })
    }

    /** Two recipe clues of one day under one headline share it; each body keeps its own line, and nothing is dropped. */
    @Test
    fun neighbouringStoriesUnderOneHeadlineShareIt() {
        val clueA = GazetteDigest.Story(GazetteDigest.Kind.CLUE, "A recipe clue", "Recipe clue for the Iron sword with Ember Resin. This combination can make a signature weapon.", eventIds = listOf("e1"), opensNotebook = true)
        val clueB = GazetteDigest.Story(GazetteDigest.Kind.CLUE, "A recipe clue", "Recipe clue for the Iron sword with Ember Resin. This recipe needs a binding catalyst.", eventIds = listOf("e2"), details = listOf("d"))
        val sale = GazetteDigest.Story(GazetteDigest.Kind.FIRST_SALE, "First sale", "Your first sale. Una Oakley bought Iron Sword.")
        val merged = mergedStories(listOf(clueA, clueB, sale, clueA))
        assertEquals(listOf("A recipe clue", "First sale", "A recipe clue"), merged.map { it.heading })
        assertEquals(clueA.body + "\n" + clueB.body, merged[0].body)
        assertEquals(listOf("e1", "e2"), merged[0].eventIds)
        assertEquals(listOf("d"), merged[0].details)
        assertTrue(merged[0].opensNotebook)
        // Not neighbours, or another kind under the same words: kept apart.
        assertEquals(listOf(sale), mergedStories(listOf(sale)))
        assertEquals(2, mergedStories(listOf(clueA, clueA.copy(kind = GazetteDigest.Kind.PAIRING))).size)
    }

    @Test
    fun theResultCardIsTheWeaponsCardWithItsRecipeAndWhatRarityBuys() {
        val d = engine.itemDetail(forged, blade.id)!!.copy(affixes = listOf(Property("Flaming", "Its strikes burn.")), flaws = listOf(Property("Brittle", "It wears faster.")))
        val details = TipAction("Full details", "reveal_full_details") {}
        val tip = resultTip(d, config, "Sword of Iron and Ember Resin.", rarityRule = true, actions = listOf(details))
        assertWhole(tip)
        assertEquals(d.name, tip.name)
        assertEquals(listOf(details), tip.actions)
        // The properties lead the card untitled: a "+" or "−" says which is which.
        val properties = tip.sections.flatMap { it.lines }.filter { it.tone == TipTone.BONUS || it.tone == TipTone.DRAWBACK }
        assertEquals(listOf("Flaming" to TipTone.BONUS, "Brittle" to TipTone.DRAWBACK), properties.map { it.title to it.tone })
        assertEquals(listOf("Sword of Iron and Ember Resin"), tip.sections.single { it.title == "Made of" }.lines.map { it.text })
        assertEquals(RARITY_RULE, tip.sections.single { it.title == "Rarity" }.lines.joinToString(" ") { it.text })
        assertTrue("no renown on a new weapon", tip.stats.none { it.label == "Fame" })
        assertTrue(resultTip(d, config, "x.", rarityRule = false, actions = emptyList()).sections.single { it.title == "Rarity" }.lines.isEmpty())
    }

    @Test
    fun whatTheNotebookLearnedIsOneLineAndWholeOnItsCard() {
        val gains = ForgeLearningUi(listOf(LearnedUi("First notes", "Iron and Ember Resin: a warm start."), LearnedUi("Recipe clue found", "Ember Sword: needs a fine metal.")), note = null, attempt = "The known signature took again: Ember Sword.")
        assertEquals("First notes · Recipe clue found", learningLine(gains))
        val tip = learningTip(gains)
        assertWhole(tip)
        assertEquals(gains.changes.map { it.title to it.line }, tip.sections.first().lines.filter { it.tone == TipTone.BONUS }.map { it.title to it.text })
        assertEquals(listOf(gains.attempt), tip.sections.single { it.title == "Signature" }.lines.map { it.text })
        assertEquals("No new findings.", learningLine(ForgeLearningUi(emptyList(), "No new findings.")))
        assertEquals("met every condition", learningLine(ForgeLearningUi(emptyList(), null, "met every condition")))
        assertNull(learningLine(ForgeLearningUi(emptyList(), null)))
    }

    @Test
    fun eachSiegeTraitSaysWhatItChangesFromItsOwnNumbersWithTheRightSign() {
        val traits = content.siegeTraits.associateBy { it.id }
        fun lines(id: String) = siegeTraitLines(traits.getValue(id), config).map { it.text to it.tone }
        val floor = (config.conditionFloorFactor * 100).toInt()
        assertEquals(listOf(
            "A weapon at condition 0 counts for 60% of its power, usually $floor%" to TipTone.DRAWBACK,
            "The defenders' weapons lose ${config.wearPerSiege * 2} condition in the siege, usually ${config.wearPerSiege}" to TipTone.DRAWBACK,
        ), lines(Depth.LONG_ASSAULT))
        assertEquals(listOf("The attackers are 5% stronger" to TipTone.DRAWBACK, "The militia and the armory count 75% more" to TipTone.BONUS), lines(Depth.MANY_BREACHES))
        assertEquals(listOf("The attackers are 15% weaker" to TipTone.BONUS, "A weapon at condition 0 counts for 40% of its power, usually $floor%" to TipTone.DRAWBACK), lines(Depth.HUNGRY_HORDE))
        assertEquals(listOf("The attackers are 10% weaker" to TipTone.BONUS, "The militia and the armory do not fight" to TipTone.DRAWBACK), lines(Depth.NIGHT_ASSAULT))
        for (t in content.siegeTraits) {
            val tip = siegeTraitTip(t, config, 12)
            assertWhole(tip)
            assertTrue(tip.kind, tip.kind.endsWith("day 12"))
            assertTrue("${t.id} has an effect", tip.sections.single { it.title == "In this siege" }.lines.isNotEmpty())
            assertEquals(t.counsel, tip.sections.single { it.title == "What helps" }.lines.joinToString(" ") { it.text })
        }
    }

    @Test
    fun theBesiegersCardCarriesTheForecastAndTheMatchup() {
        val siege = TownSiegeUi(FactionId("f"), "Ashclaw Raiders", "Frost", "Fire", 80, 95, "Vharsk leads them", 70, 100, "Rising", emptyList(), "Spring", emptyList())
        val tip = besiegerTip(siege, "Outmatched", Battle.SiegeOdds.OUTMATCHED, null)!!
        assertWhole(tip)
        assertEquals(listOf("Raid" to "95", "Town defense" to "80", "Outlook" to "Outmatched"), tip.stats.map { it.label to it.value })
        assertEquals(FactTone.WARN, tip.stats.single { it.label == "Outlook" }.tone)
        assertEquals(listOf("Weak to Frost" to TipTone.BONUS, "Resists Fire" to TipTone.DRAWBACK, "Vharsk leads them" to TipTone.DRAWBACK), tip.sections.single().lines.map { it.text to it.tone })
        assertNull("nobody pressing, no card", besiegerTip(siege.copy(foe = null), null, null, null))
        assertEquals(FactTone.GOOD, oddsTone(Battle.SiegeOdds.STRONG))
        assertEquals(FactTone.PLAIN, oddsTone(Battle.SiegeOdds.EVEN))
        assertEquals(FactTone.WARN, oddsTone(Battle.SiegeOdds.DIRE))
        assertEquals(FactTone.PLAIN, oddsTone(null))
    }

    @Test
    fun anUpgradesCardSaysItsLevelItsNextLevelAndWhatItDoes() {
        val u = content.upgrades.first()
        val next = Legacy.preview(u.id, 1, content, config)!!.text
        val tip = upgradeTip(u, 0, next, u.costPerLevel.first(), "t")
        assertWhole(tip)
        assertEquals("0 of ${u.maxLevel}", tip.stats.single { it.label == "Level" }.value)
        assertEquals("${u.costPerLevel.first()} points", tip.stats.single { it.label == "Next level" }.value)
        assertEquals(listOf(next to TipTone.BONUS), tip.sections.single { it.title == "Next level" }.lines.map { it.text to it.tone })
        assertEquals(u.description, tip.sections.single { it.title == "What it does" }.lines.single().text)
        val full = upgradeTip(u, u.maxLevel, null, null, "t")
        assertEquals(listOf("Fully unlocked"), full.sections.single { it.title == "Next level" }.lines.map { it.text })
        assertTrue(full.stats.none { it.label == "Next level" })
    }

    @Test
    fun theRunsRewardAddsUpOnItsCardAndALegendKeepsEveryLine() {
        val end = RunEndResult(RunId("r"), 2, 31, "The forge fell.", 10, 6, 4, 5, listOf("FIRST_SALE", "SIEGE_SURVIVED"), emptyList(), null, LegacyProfile())
        val tip = runPointsTip(end, milestone = { "label $it" })
        assertWhole(tip)
        assertEquals("25 legacy points", tip.name)
        assertEquals(end.totalPoints, tip.stats.sumOf { it.value.toInt() })
        assertEquals(listOf("label FIRST_SALE", "label SIEGE_SURVIVED"), tip.sections.single().lines.map { it.text })
        assertTrue(tip.sections.single().lines.all { it.tone == TipTone.BONUS })
        val legend = LegendUi("Ashen Blade, era 1", listOf("A rare sword", "Carried by Wren", "Held the wall twice"), lostToTime = false)
        assertEquals(legend.lines, legendTip(legend, "t").sections.single().lines.map { it.text })
    }

    @Test
    fun thePracticeFightsEffectsAreAbilityRowsAndItsStatusesComeFromTheirDefinitions() {
        val weapon = CombatSandbox.blade("w_bench", LaunchContent.SWORD, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, LaunchContent.DRAGON_OIL, craft = WeaponRules.CRAFT)
        var sawFired = false
        var sawSilent = false
        for (target in Bench.targets) {
            val report = Bench.run(weapon, false, Bench.defaultWielder(weapon.familyId, content), "alone", target.id, content, config)
            val ui = benchUi(report)
            val fired = benchFiredLines(ui)
            val silent = benchSilentLines(ui)
            assertEquals(ui.fired.map { it.name }, fired.map { it.title })
            assertEquals(ui.silent.map { it.name to it.why }, silent.map { it.title to it.text })
            assertTrue(fired.all { it.tone == TipTone.ABILITY && it.text.isNotBlank() && it.limit!!.startsWith("Triggered") })
            ui.fired.zip(fired).forEach { (f, l) ->
                assertEquals(f.chain.last(), l.text)
                assertEquals(if (f.times == 1) "Triggered once" else "Triggered ${f.times} times", l.limit)
                assertEquals(f.chain.dropLast(1).takeIf { it.isNotEmpty() }?.joinToString(" → "), l.trigger)
            }
            assertTrue(silent.all { it.tone == TipTone.CONDITION })
            assertEquals((report.fired.map { it.rule.effect } + report.silent.map { it.rule.effect }).flatMap { it.statuses() }.toSet(), ui.stats.toSet())
            val made = benchSyntheticTip(ui)
            assertWhole(made)
            assertEquals(ui.synthetic, made.sections.single().lines.map { it.text })
            sawFired = sawFired || fired.isNotEmpty()
            sawSilent = sawSilent || silent.isNotEmpty()
        }
        assertTrue("the fixture triggers something somewhere", sawFired)
        assertTrue("and leaves something silent somewhere", sawSilent)
    }

    @Test
    fun aDiscoveredEffectKeepsTheStatusesItsDefinitionNames() {
        val combat = content.combat!!
        val journal = engine.newRun(LegacyProfile(), 42).legacy.journal.copy(learnedRules = setOf("core_bronze"))
        val rule = fightNotes(journal, combat).learned.single()
        assertEquals(Interactions.page(journal, combat).learned.single().statuses(), rule.stats)
        assertFalse(rule.name.isBlank())
    }
}
