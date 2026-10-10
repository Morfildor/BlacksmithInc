package com.example.blacksmithproject

import com.example.blacksmithproject.ui.forgeOptions
import com.example.blacksmithproject.ui.RecipeSlot
import com.example.blacksmithproject.ui.staleFree
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.model.Journal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Browsing is free and learning is told once: the notebook's two shortcuts change the draft only; a forge's lesson is on its result and gone with it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForgeBrowsingTest : ShopDayTestBase() {
    @Test
    fun usingAPairingAndTryingAnUntriedOneChangeTheDraftAndNothingElse() = vmTest {
        val vm = open(planning(fresh))
        val saved = vm.playing().state
        vm.updateDraft { it.copy(familyId = forgeSword.familyId) }
        vm.tryUntried(); advanceUntilIdle()
        val first = vm.playing()
        assertSame("no command was sent: the saved game is the same object", saved, first.state)
        assertEquals(Dest.FORGE, first.dest)
        assertEquals("the weapon chosen before stays", forgeSword.familyId, first.draft.familyId)
        assertTrue((saved.materials[first.draft.coreId] ?: 0) > 0 && (saved.materials[first.draft.augmentId] ?: 0) > 0)
        assertEquals("Untried ingredients selected. No materials spent.", first.notice)

        vm.usePairing(Journal.augmentFamilyKey(forgeSword.augmentId, engine.content.families.last().id)); advanceUntilIdle()
        val second = vm.playing()
        assertSame(saved, second.state)
        assertEquals(forgeSword.augmentId to engine.content.families.last().id, second.draft.augmentId to second.draft.familyId)
        assertEquals("the metal is not part of a weapon pairing and stays", first.draft.coreId, second.draft.coreId)
        // A key that is no pairing changes nothing at all.
        vm.usePairing("sig:anything"); vm.usePairing("ca:broken"); advanceUntilIdle()
        assertEquals(second.draft, vm.playing().draft)
    }

    @Test
    fun whatAForgeTaughtIsOnItsResultAndLeavesWithIt() = vmTest {
        val vm = open(planning(fresh))
        assertNull(vm.playing().learning)
        vm.dispatch(forgeSword); advanceUntilIdle()
        val result = vm.playing()
        assertNotNull(result.revealWeaponId)
        val titles = result.learning!!.changes.map { it.title }
        assertEquals(listOf("New observation", "New observation"), titles.take(2))
        // This recipe is also the base of a signature: the miss earns a rung, told in the journal's words and never by the signature's name.
        assertTrue(titles.drop(2).all { it == "Recipe clue earned" })
        val hidden = com.tinyblacksmith.core.crafting.SignatureCatalog.forRecipe(forgeSword)
        if (hidden != null) assertTrue(result.learning!!.changes.none { hidden.name in it.line })
        vm.storeForged(); advanceUntilIdle()
        assertNull(vm.playing().learning)
        assertEquals("the draft is as it was before the forge", result.draft, vm.playing().draft)
    }

    @Test
    fun aRestockedMaterialIsNoLongerSaidToBeOut() {
        val core = engine.content.materials(MaterialCategory.CORE).first()
        val out = fresh.copy(materials = fresh.materials + (core.id to 0))
        val inspected = engine.forgeOptions(out, ForgeDraft(), RecipeSlot.METAL, null).first { it.id == core.id.value }
        assertEquals("while it is out, the tray keeps saying so", inspected.id, staleFree(inspected.id, engine.forgeOptions(out, ForgeDraft(), RecipeSlot.METAL, null))?.id)
        val bought = out.copy(materials = out.materials + (core.id to 1))
        assertNull("once bought, the missing-material line is gone", staleFree(inspected.id, engine.forgeOptions(bought, ForgeDraft(), RecipeSlot.METAL, null)))
    }
}
