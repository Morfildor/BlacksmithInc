package com.example.blacksmithproject

import com.example.blacksmithproject.ui.AskUi
import com.example.blacksmithproject.ui.BriefUi
import com.example.blacksmithproject.ui.FactTone
import com.example.blacksmithproject.ui.RecipeSlot
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.facts
import com.example.blacksmithproject.ui.fightFacts
import com.example.blacksmithproject.ui.forResult
import com.example.blacksmithproject.ui.forgeWorkbench
import com.example.blacksmithproject.ui.trayChange
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.ForgeOutlook
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.sim.CombatSandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The V2 Forge's short facts: what each closed surface says at a glance, read from the same models as its details. */
class ForgeV2FactsTest {
    private val engine = GameEngine()
    private val content = engine.content
    private val guild = engine.newRun(LegacyProfile(), 42, charterId = GuildContent.TOWNS_LAST_HOPE)
    private val draft = ForgeDraft(familyId = LaunchContent.SWORD, coreId = LaunchContent.IRON, augmentId = LaunchContent.STORMGLASS)

    @Test
    fun theOutlookSaysTheLikelyQualityAndWhoItSuitsAndNotTheEnergyAgain() {
        val ui = engine.forgeWorkbench(guild, draft, emptyList()).outlook!!
        val core = ForgeOutlook.of(guild, content, engine.config, Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.STORMGLASS, null, draft.risk, null))!!
        assertEquals(core.qualityLow to core.qualityHigh, ui.qualityLow to ui.qualityHigh)
        val facts = ui.facts()
        assertEquals("Likely quality", facts.first().label)
        assertEquals(if (core.qualityLow == core.qualityHigh) "${core.qualityLow}" else "${core.qualityLow} to ${core.qualityHigh}", facts.first().value)
        val suits = facts.firstOrNull { it.label == "Suits" }
        if (core.wielders.isEmpty()) assertNull(suits)
        else {
            // A few words: two names at most, the rest counted.
            assertTrue(suits!!.value, core.wielders.take(2).all { it in suits.value })
            if (core.wielders.size > 2) assertTrue(suits.value, suits.value.endsWith("and ${core.wielders.size - 2} more"))
        }
        assertTrue("the energy is on the Forge action", facts.none { "energy" in it.value.lowercase() })
    }

    @Test
    fun aBriefsFactsTickWhatTheDraftAnswersAndEndWithItsStanding() {
        val b = BriefUi("Brann's commission · 80 gold · Due in 3 days", listOf(AskUi("Sword", true), AskUi("Frost", false), AskUi("Quality 35+", null)), accepted = false, status = "Not accepted yet")
        assertEquals(listOf("✓ Sword", "Frost", "Quality 35+", "Not accepted yet"), b.facts().map { it.value })
    }

    @Test
    fun aFightLeadsWithWhoIsStillStanding() {
        for (f in CombatSandbox.fixtures) {
            val result = Fight.resolve(f.setup)
            val facts = fightFacts(result)
            val party = result.actors.filter { it.side == Side.PARTY }
            val enemies = result.actors.filter { it.side == Side.ENEMY }
            assertEquals(f.id, "${party.count { !it.downed }} of ${party.size} standing", facts.single { it.label == "Party" }.value)
            assertEquals(f.id, "${enemies.count { !it.downed }} of ${enemies.size} standing", facts.single { it.label == "Enemies" }.value)
            val cracked = party.count { it.fractured }
            assertEquals(f.id, cracked > 0, facts.any { it.tone == FactTone.WARN && "cracked" in it.value })
        }
    }

    @Test
    fun aFreshWeaponsFullConditionIsOneFactAndItsQualityKeepsItsBar() {
        val forged = engine.handle(engine.newRun(LegacyProfile(), 42L), Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)).stateOrThrow()
        val blade = forged.weapons.values.single()
        val fresh = engine.itemDetail(forged, blade.id)!!.forResult()
        assertEquals(listOf("Power", "Quality", "Condition"), fresh.stats.map { it.label })
        assertEquals(100, fresh.stats.single { it.label == "Quality" }.max)
        assertEquals(if (blade.condition == 100) null else 100, fresh.stats.single { it.label == "Condition" }.max)
        val worn = forged.copy(weapons = mapOf(blade.id to blade.copy(condition = 40)))
        assertEquals("a worn weapon keeps its bar", 100, engine.itemDetail(worn, blade.id)!!.forResult().stats.single { it.label == "Condition" }.max)
    }

    @Test
    fun aComparisonKeepsItsDecisionFactsApartFromTheRulesAndNotes() {
        val other = content.materials(MaterialCategory.AUGMENT).first { (guild.materials[it.id] ?: 0) > 0 && it.element != content.material(LaunchContent.STORMGLASS).element }
        val changes = engine.trayChange(guild, draft, draft.copy(augmentId = other.id), RecipeSlot.AUGMENT, emptyList(), null)!!.changes
        val facts = changes.filter { !it.rule }.map { it.text }
        val rules = changes.filter { it.rule }.map { it.text }
        assertTrue(facts.toString(), facts.any { it.startsWith("Costs ") } && facts.any { " element instead of " in it })
        assertTrue(rules.toString(), rules.all { it.startsWith("Gains the effect") || it.startsWith("Loses the effect") || " needs " in it || it.endsWith("now has what it needs") || it.startsWith("Drawback: ") || it.startsWith("Metal + infusion: ") || it.startsWith("Infusion + weapon: ") || it.startsWith("Signature: ") })
        assertFalse("nothing is dropped: every change is a fact or a rule line", changes.isEmpty())
    }
}
