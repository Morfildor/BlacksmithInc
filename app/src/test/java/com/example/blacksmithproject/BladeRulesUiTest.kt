package com.example.blacksmithproject

import com.example.blacksmithproject.ui.RecipeSlot
import com.example.blacksmithproject.ui.bladeRules
import com.example.blacksmithproject.ui.detail.StockAction
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.detail.toCommand
import com.example.blacksmithproject.ui.draftRules
import com.example.blacksmithproject.ui.forgeOptions
import com.example.blacksmithproject.ui.forgeWorkbench
import com.example.blacksmithproject.ui.loanRows
import com.example.blacksmithproject.ui.loanTargets
import com.example.blacksmithproject.ui.loanUi
import com.example.blacksmithproject.ui.stepCharter
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.MemberStatus
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.sim.CombatSandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a blade does in a fight, what a draft will give it, and a blade's standing with the guild: pure readings of the save and the catalog. */
class BladeRulesUiTest {
    private val engine = GameEngine()
    private val combat = engine.content.combat!!
    private val guild = engine.newRun(LegacyProfile(), 42, charterId = GuildContent.TOWNS_LAST_HOPE)
    private val classic = engine.newRun(LegacyProfile(), 42)

    private fun GameState.with(vararg blades: Weapon) = copy(weapons = weapons + blades.associateBy { it.id })
    private fun GameState.send(command: Command) = (engine.handle(this, command) as CommandOutcome.Accepted).state

    @Test
    fun aCapacitorSwordListsItsElementAndCatalystRulesInTheCatalogsWords() {
        val rules = bladeRules(CombatSandbox.capacitor, combat)
        assertEquals("One clean strike.", rules.pattern)
        assertEquals(listOf("Arc" to "element", "Capacitor" to "catalyst", "Charge burst" to "catalyst"), rules.road.map { it.name to it.source })
        assertEquals(CombatContent.CAPACITOR.description, rules.road.first { it.name == "Capacitor" }.description)
        assertTrue(rules.homeOnly.isEmpty())
        // The same sword without Binding Salt: the element's rule alone.
        assertEquals(listOf("Arc"), bladeRules(CombatSandbox.plainSword, combat).road.map { it.name })
    }

    @Test
    fun aBrittleBladeListsTheCrackRuleAsAFlaw() {
        val brittle = bladeRules(CombatSandbox.bellblade, combat).road.first { it.name == "Brittle" }
        assertEquals("flaw", brittle.source)
        assertEquals(combat.affixEffects.getValue(LaunchContent.BRITTLE).single().description, brittle.description)
    }

    @Test
    fun aRuleABladeHasOnlyAtHomeIsListedApart() {
        val sentient = CombatSandbox.blade("w_s", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, affixes = listOf(LaunchContent.SENTIENT))
        val rules = bladeRules(sentient, combat)
        assertEquals(listOf("Sentient (at home)" to "buff"), rules.homeOnly.map { it.name to it.source })
        assertTrue(rules.road.any { it.name == "Sentient (on the road)" })
        assertFalse(rules.road.any { it.name == "Sentient (at home)" })
    }

    @Test
    fun aClassicRunHasNoFightSection() {
        val blade = CombatSandbox.capacitor
        assertNull(engine.bladeRules(classic, blade))
        assertNull(engine.itemDetail(classic.with(blade), blade.id)!!.rules)
        assertNull(engine.itemDetail(classic.with(blade), blade.id)!!.loan)
        assertTrue(draftRules(classic, engine.content, LaunchContent.STORMGLASS, LaunchContent.BINDING_SALT).isEmpty())
        assertTrue(engine.forgeOptions(classic, ForgeDraft(), RecipeSlot.CATALYST, null).all { it.fight.isEmpty() })
        assertTrue(classic.loanRows().isEmpty())
        // The same blade in a guild run carries the section on its card.
        assertEquals(bladeRules(blade, combat), engine.itemDetail(guild.with(blade), blade.id)!!.rules)
    }

    @Test
    fun theForgeSaysWhatTheAugmentAndTheCatalystWillGiveTheBlade() {
        val draft = ForgeDraft(familyId = LaunchContent.SWORD, coreId = LaunchContent.IRON, augmentId = LaunchContent.STORMGLASS, catalystId = LaunchContent.BINDING_SALT, mode = ForgeMode.ADVANCED)
        val bench = engine.forgeWorkbench(guild, draft, emptyList())
        assertEquals(listOf("Arc", "Capacitor", "Charge burst"), bench.fight.map { it.name })
        assertEquals(listOf("Stormglass", "Binding Salt", "Binding Salt"), bench.fight.map { it.source })
        // A quick forge carries no catalyst, whatever the draft still holds.
        assertEquals(listOf("Arc"), engine.forgeWorkbench(guild, draft.copy(mode = ForgeMode.QUICK), emptyList()).fight.map { it.name })
        assertTrue(engine.forgeWorkbench(classic, draft, emptyList()).fight.isEmpty())
        // Each catalyst says its rules where it is chosen; "None" has none.
        val tray = engine.forgeOptions(guild, draft, RecipeSlot.CATALYST, null)
        assertEquals(listOf("Capacitor: ${CombatContent.CAPACITOR.description}", "Charge burst: ${CombatContent.CHARGE_BURST.description}"), tray.first { it.id == LaunchContent.BINDING_SALT.value }.fight)
        assertTrue(tray.first { it.id == null }.fight.isEmpty())
        assertTrue(tray.filter { it.id != null }.all { it.fight.isNotEmpty() })
    }

    @Test
    fun aBladeInTheShopCanBeLentToAMemberInTownAndCalledBack() {
        val blade = CombatSandbox.capacitor
        val stocked = guild.with(blade)
        val members = stocked.guild!!.members
        val targets = engine.loanTargets(stocked)
        assertEquals(members.map { it.heroId }, targets.map { it.heroId })
        assertTrue(targets.all { it.enabled && it.reason == null })
        val inShop = engine.loanUi(stocked, blade)!!
        assertNull(inShop.holder)
        assertEquals(targets, inShop.targets)

        val to = members.first().heroId
        val lent = stocked.send(StockAction.Loan(to).toCommand(blade.id))
        val detail = engine.itemDetail(lent, blade.id)!!
        assertEquals(lent.heroes.getValue(to).fullName, detail.loan!!.holder)
        assertNull(detail.loan!!.recallBlocked)
        assertNull("the shop's own actions are not offered for a loaned blade", detail.stock)
        assertEquals(listOf(blade.id), lent.loanRows().map { it.weaponId })
        assertTrue(engine.loanTargets(lent).first { it.heroId == to }.carrying.contains(blade.name))

        // The holder on the road: the blade cannot be called back, and nobody away can take another.
        val away = lent.copy(guild = lent.guild!!.copy(members = lent.guild!!.members.map { if (it.heroId == to) it.copy(status = MemberStatus.AWAY) else it }))
        assertEquals("${lent.heroes.getValue(to).name} is away with the party", engine.loanUi(away, away.weapons.getValue(blade.id))!!.recallBlocked)
        assertFalse(engine.loanTargets(away).first { it.heroId == to }.enabled)

        val back = lent.send(StockAction.Recall.toCommand(blade.id))
        assertTrue(back.weapons.getValue(blade.id).isInStorage)
        assertNotNull(engine.itemDetail(back, blade.id)!!.stock)
    }

    @Test
    fun theCharterArrowsWrapThroughEveryCharterAndTheClassicShop() {
        val charters = engine.content.guild!!.charters
        assertNull(stepCharter(charters, charters.first().id, -1))
        assertEquals(charters.first().id, stepCharter(charters, null, 1))
        assertEquals(charters[1].id, stepCharter(charters, charters.first().id, 1))
        assertEquals(charters.last().id, stepCharter(charters, null, -1))
    }
}
