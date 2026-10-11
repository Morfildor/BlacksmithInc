package com.example.blacksmithproject

import com.example.blacksmithproject.ui.RecipeSlot
import com.example.blacksmithproject.ui.forgeOptions
import com.example.blacksmithproject.ui.forgeWorkbench
import com.example.blacksmithproject.ui.shopUi
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Journal
import com.tinyblacksmith.core.model.KnowledgeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Forge workbench as a pure reading of the save and the draft: slots, the one action, field notes and the commission brief. */
class ForgeWorkbenchModelTest : ShopDayTestBase() {
    private val content get() = engine.content
    private val family get() = content.families.first()
    private val core get() = content.materials(MaterialCategory.CORE).first { (fresh.materials[it.id] ?: 0) > 0 }
    private val augment get() = content.materials(MaterialCategory.AUGMENT).first { (fresh.materials[it.id] ?: 0) > 0 }
    private val ready get() = ForgeDraft(familyId = family.id, coreId = core.id, augmentId = augment.id)

    private fun bench(draft: ForgeDraft, state: com.tinyblacksmith.core.model.GameState = fresh) = engine.forgeWorkbench(state, draft, engine.shopUi(state).requests)

    @Test
    fun anEmptyDraftAsksForTheWeaponFirstAndThenEachMissingIngredient() {
        val empty = bench(ForgeDraft())
        assertEquals(RecipeSlot.WEAPON, empty.next)
        assertEquals("Choose a weapon type", empty.action.note)
        assertFalse(empty.action.enabled)
        assertNull(empty.command)
        assertEquals(listOf(RecipeSlot.WEAPON, RecipeSlot.METAL, RecipeSlot.AUGMENT), empty.slots.map { it.slot })

        val one = bench(ForgeDraft(familyId = family.id))
        assertEquals(RecipeSlot.METAL, one.next)
        assertEquals("Choose a metal", one.action.note)
        val two = bench(ForgeDraft(familyId = family.id, coreId = core.id))
        assertEquals(RecipeSlot.AUGMENT, two.next)
        assertEquals("Choose an augment", two.action.note)
        assertTrue(two.notes.isEmpty())
    }

    @Test
    fun aReadyRecipeIsOneActionThatSaysItsCostAndCarriesTheDraftUnchanged() {
        val b = bench(ready)
        assertNull(b.next)
        assertEquals("Forge · ${engine.config.quickForgeEnergy} energy", b.action.label)
        assertTrue(b.action.enabled)
        assertNull(b.action.note)
        assertEquals(Command.Forge(ForgeMode.QUICK, family.id, core.id, augment.id, null, ready.risk, null), b.command)
        assertEquals("${core.name} ${family.name.lowercase()}", b.title)
        assertEquals(listOf(null, fresh.materials[core.id], fresh.materials[augment.id]), b.slots.map { it.stock })
    }

    @Test
    fun advancedAddsCatalystAndTechniqueAndQuickNeverSendsThem() {
        val catalyst = content.materials(MaterialCategory.CATALYST).first()
        val advanced = bench(ready.copy(mode = ForgeMode.ADVANCED, catalystId = catalyst.id, technique = Technique.QUENCH))
        assertEquals(listOf(RecipeSlot.WEAPON, RecipeSlot.METAL, RecipeSlot.AUGMENT, RecipeSlot.CATALYST, RecipeSlot.TECHNIQUE), advanced.slots.map { it.slot })
        assertEquals("Forge · ${engine.config.advancedForgeEnergy} energy", advanced.action.label)
        // A draft that still holds them in Quick (it should not) cannot leak them into the command.
        val quick = bench(ready.copy(catalystId = catalyst.id, technique = Technique.QUENCH))
        assertNull(quick.command!!.catalystId)
        assertNull(quick.command!!.technique)
    }

    @Test
    fun aMissingMaterialIsNamedWithItsRestock() {
        val out = fresh.copy(materials = fresh.materials + (core.id to 0))
        val b = bench(ready, out)
        assertFalse(b.action.enabled)
        assertEquals("No ${core.name} left", b.action.note)
        assertEquals(core.id, b.action.restock)
        // Still shown in the picker, apart from what can be used.
        val options = engine.forgeOptions(out, ready, RecipeSlot.METAL, null)
        assertEquals(0, options.first { it.id == core.id.value }.stock)
        assertTrue(options.indexOfFirst { it.stock == 0 } > options.indexOfLast { (it.stock ?: 1) > 0 })
    }

    @Test
    fun overworkSaysItsPriceAndNoEnergyOffersEndDay() {
        val cost = engine.config.quickForgeEnergy
        val tired = bench(ready, fresh.copy(energy = cost - 1))
        assertTrue(tired.action.enabled)
        assertEquals("You'll have 1 less energy tomorrow", tired.action.note)
        assertFalse(tired.action.endDay)
        val spent = bench(ready, fresh.copy(energy = 0, overworkToday = engine.config.maxOverworkPerDay))
        assertFalse(spent.action.enabled)
        assertTrue(spent.action.endDay)
    }

    @Test
    fun fieldNotesKeepTheTwoRelationshipsApartAndSayNothingOfAnUntriedOne() {
        val ca = Journal.coreAugmentKey(core.id, augment.id)
        val af = Journal.augmentFamilyKey(augment.id, family.id)
        val untried = bench(ready).notes
        assertEquals(listOf("Metal + augment", "Augment + weapon"), untried.map { it.label })
        assertEquals(listOf("Untried", "Untried"), untried.map { it.stage })
        assertTrue(untried.all { it.hint == "Forge to learn" })

        val knowing = fresh.copy(legacy = fresh.legacy.copy(journal = Journal(interactions = mapOf(ca to KnowledgeState.OBSERVED, af to KnowledgeState.UNDERSTOOD))))
        val notes = bench(ready, knowing).notes
        assertEquals(listOf("Studying", "Learned"), notes.map { it.stage })
        assertTrue("an observed pairing stays tentative", notes[0].hint.startsWith("Seems"))
        assertEquals(com.tinyblacksmith.core.crafting.Journal.hint(knowing.legacy.journal, content, af), notes[1].hint)
    }

    @Test
    fun onlyTheChosenOpenCommissionIsBriefedAndItIsNeverCalledAccepted() {
        val buyer = fresh.aliveHeroes().first()
        val offer = Commission(CommissionId("c1"), buyer.id, family.id, 35, 165, fresh.day, fresh.day + 3, CommissionStatus.OFFERED, element = augment.element)
        val other = offer.copy(id = CommissionId("c2"), deadlineDay = fresh.day + 5)
        val state = fresh.copy(commissions = mapOf(offer.id to offer, other.id to other))
        assertNull(bench(ready, state).brief)
        val brief = bench(ready.copy(commissionId = offer.id), state).brief!!
        assertEquals("${buyer.name}'s commission · 165 gold · Due in 3 days", brief.title)
        assertFalse(brief.accepted)
        assertEquals(listOf(true, true, null), brief.asks.map { it.matches })
        assertEquals("Quality 35+", brief.asks.last().text)
        // A commission that has closed leaves no brief behind.
        val closed = state.copy(commissions = state.commissions + (offer.id to offer.copy(status = CommissionStatus.EXPIRED)))
        assertNull(bench(ready.copy(commissionId = offer.id), closed).brief)
    }
}
