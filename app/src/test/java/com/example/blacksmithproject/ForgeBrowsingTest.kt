package com.example.blacksmithproject

import com.example.blacksmithproject.ui.forgeOptions
import com.example.blacksmithproject.ui.forgeWorkbench
import com.example.blacksmithproject.ui.idIn
import com.example.blacksmithproject.ui.place
import com.example.blacksmithproject.ui.roadLine
import com.example.blacksmithproject.ui.trayChange
import com.example.blacksmithproject.ui.withSlot
import org.junit.Assert.assertFalse
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
        assertEquals("Selected a new pairing. No materials spent.", first.notice)

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
        assertEquals(listOf("First notes", "First notes"), titles.take(2))
        // This recipe is also the base of a signature: the miss earns a rung, told in the journal's words and never by the signature's name.
        assertTrue(titles.drop(2).all { it == "Recipe clue found" })
        val hidden = com.tinyblacksmith.core.crafting.SignatureCatalog.forRecipe(forgeSword)
        if (hidden != null) assertTrue(result.learning!!.changes.none { hidden.name in it.line })
        vm.storeForged(); advanceUntilIdle()
        assertNull(vm.playing().learning)
        assertEquals("the draft is as it was before the forge", result.draft, vm.playing().draft)
    }

    /** A complete Quick draft from [state]'s stock: the first family, metal and augment in stock. */
    private fun ready(state: com.tinyblacksmith.core.model.GameState = fresh) = ForgeDraft(
        familyId = engine.content.families.first().id,
        coreId = engine.content.materials(MaterialCategory.CORE).first { (state.materials[it.id] ?: 0) > 0 }.id,
        augmentId = engine.content.materials(MaterialCategory.AUGMENT).first { (state.materials[it.id] ?: 0) > 0 }.id,
    )

    @Test
    fun comparingAAgainstBAndBackRestoresTheDraftAndEveryValueAndSpendsNothing() = vmTest {
        val vm = open(planning(fresh))
        val saved = vm.playing().state
        val a = ready()
        vm.updateDraft { a }; advanceUntilIdle()
        val requests = vm.playing().shop.requests
        val before = engine.forgeWorkbench(saved, a, requests)
        // The metal tray opens on A; B is tried, then A again: the tray stays open throughout (`place`, never a command).
        val options = engine.forgeOptions(saved, a, RecipeSlot.METAL, null)
        val b = options.first { it.usable && it.id != a.coreId!!.value }
        vm.updateDraft { engine.place(it, RecipeSlot.METAL, b) }; advanceUntilIdle()
        val tried = vm.playing().draft
        assertEquals(b.id, tried.coreId!!.value)
        val change = engine.trayChange(saved, a.withSlot(RecipeSlot.METAL, a.idIn(RecipeSlot.METAL)), tried, RecipeSlot.METAL, requests, null)!!
        assertEquals(engine.content.material(a.coreId!!).name, change.from)
        assertTrue("B differs from A in at least its stock and price", change.changes.isNotEmpty())
        vm.updateDraft { engine.place(it, RecipeSlot.METAL, options.first { o -> o.id == a.coreId!!.value }) }; advanceUntilIdle()
        val back = vm.playing()
        assertEquals("A, B, A: the draft is the one the tray opened on", a, back.draft)
        assertEquals("and every value shown with it", before, engine.forgeWorkbench(back.state, back.draft, back.shop.requests))
        assertEquals(engine.forgeOptions(saved, a, RecipeSlot.METAL, null), engine.forgeOptions(back.state, back.draft, RecipeSlot.METAL, null))
        assertTrue("back where it started: nothing to compare", engine.trayChange(saved, a, back.draft, RecipeSlot.METAL, requests, null)!!.changes.isEmpty())
        assertSame("no command, no draw: the saved game (and its random streams) is the same object", saved, back.state)
        // "Put back" is the same as choosing A again.
        assertEquals(a, tried.withSlot(RecipeSlot.METAL, a.idIn(RecipeSlot.METAL)))
    }

    @Test
    fun aMaterialThatRanOutCanBeInspectedButNeverPlacedAndSaysWhereMoreComesFrom() {
        val a = ready()
        val core = engine.content.materials(MaterialCategory.CORE).first { it.id != a.coreId }
        val out = fresh.copy(materials = fresh.materials + (core.id to 0))
        val tile = engine.forgeOptions(out, a, RecipeSlot.METAL, null).first { it.id == core.id.value }
        assertFalse(tile.usable)
        assertEquals("None left. " + (engine.roadLine(out, core.id) ?: "Buy more in Supplies."), tile.blocked)
        assertTrue("an option in stock is never blocked", engine.forgeOptions(fresh, a, RecipeSlot.METAL, null).filter { it.usable }.all { it.blocked == null })
        // A material the deep road has not brought yet says so, not "buy more".
        val far = engine.content.materials(MaterialCategory.CORE).firstOrNull { engine.roadLine(fresh, it.id) != null }
        if (far != null) assertEquals("None left. ${engine.roadLine(fresh, far.id)}", engine.forgeOptions(fresh.copy(materials = fresh.materials + (far.id to 0)), a, RecipeSlot.METAL, null).first { it.id == far.id.value }.blocked)
        // The tray only inspects a blocked tile; a draft that still names it cannot be forged and names the restock.
        val bench = engine.forgeWorkbench(out, a.copy(coreId = core.id), emptyList())
        assertFalse(bench.action.enabled)
        assertEquals(core.id, bench.action.restock)
    }

    @Test
    fun theFirstChoiceOfAnEmptyPlaceHasNothingToCompareAndAnOptionalPlaceAlwaysDoes() {
        val a = ready()
        assertNull(engine.trayChange(fresh, a.copy(coreId = null), a, RecipeSlot.METAL, emptyList(), null))
        assertEquals(null, ForgeDraft().idIn(RecipeSlot.WEAPON))
        val catalyst = engine.content.materials(MaterialCategory.CATALYST).first()
        val advanced = a.copy(mode = com.tinyblacksmith.core.model.ForgeMode.ADVANCED)
        val with = engine.trayChange(fresh, advanced, advanced.copy(catalystId = catalyst.id), RecipeSlot.CATALYST, emptyList(), null)!!
        assertEquals("None", with.from)
        assertTrue(with.changes.first().text.startsWith("Restock: "))
    }

    @Test
    fun aChangeNoFactSpeaksToIsNeverCalledUnchanged() {
        val tempered = ready().copy(mode = com.tinyblacksmith.core.model.ForgeMode.ADVANCED, technique = com.tinyblacksmith.core.engine.Technique.TEMPER)
        val plain = engine.trayChange(fresh, tempered, tempered.copy(technique = null), RecipeSlot.TECHNIQUE, emptyList(), null)!!
        assertEquals(listOf("Now Plain"), plain.changes.map { it.text })
        assertTrue(engine.trayChange(fresh, tempered, tempered, RecipeSlot.TECHNIQUE, emptyList(), null)!!.changes.isEmpty())
    }

    @Test
    fun theComparisonSaysOnlyFactsTheGameAlreadyGives() {
        val a = ready()
        val iron = engine.content.material(a.coreId!!)
        val fine = engine.content.materials(MaterialCategory.CORE).first { it.tier >= engine.config.depth.legend.fineCoreTier }
        val c = engine.trayChange(fresh, a, a.copy(coreId = fine.id), RecipeSlot.METAL, emptyList(), null)!!.changes.map { it.text }
        assertTrue(c.toString(), "Restock: ${iron.price} \u2192 ${fine.price} gold, uses 1" in c)
        if (!com.tinyblacksmith.core.crafting.Forge.holdsEpic(iron.tier, engine.config)) assertTrue(c.toString(), "Can reach Epic quality" in c)
        // The journal is quoted, never more: an untried pairing is only "untried".
        assertTrue(c.toString(), c.any { it == "Metal + augment: untried" })
        // No odds, no score, no signature name anywhere.
        val names = com.tinyblacksmith.core.crafting.SignatureCatalog.all.map { it.name }
        assertTrue(c.none { line -> '%' in line || names.any { it in line } })
        // A classic run has no fight rules to gain or lose.
        assertTrue(c.none { it.startsWith("Gains the effect") || it.startsWith("Loses the effect") })
    }

    @Test
    fun aGuildRunsComparisonNamesTheRulesGainedAndLost() {
        val g = engine.guildRun(7)
        val a = ready(g)
        val other = engine.content.materials(MaterialCategory.AUGMENT).first { (g.materials[it.id] ?: 0) > 0 && it.element != engine.content.material(a.augmentId!!).element }
        val ruled = engine.trayChange(g, a, a.copy(augmentId = other.id), RecipeSlot.AUGMENT, emptyList(), null)!!.changes
        val before = engine.forgeWorkbench(g, a, emptyList()).outlook!!.certain.map { it.name }.toSet()
        val after = engine.forgeWorkbench(g, a.copy(augmentId = other.id), emptyList()).outlook!!.certain.map { it.name }.toSet()
        assertEquals((after - before).map { "Gains the effect $it" }.toSet(), ruled.filter { it.text.startsWith("Gains") }.map { it.text }.toSet())
        assertEquals((before - after).map { "Loses the effect $it" }.toSet(), ruled.filter { it.text.startsWith("Loses") }.map { it.text }.toSet())
        assertTrue(ruled.any { it.text.startsWith("Adds ") })
    }

    @Test
    fun anIncompleteComparisonKeepsPairingKnowledgeHiddenAndNeverCreatesACommand() {
        val a = ready().copy(familyId = null)
        val b = engine.content.materials(MaterialCategory.CORE).first { it.id != a.coreId }
        val state = fresh.copy(legacy = fresh.legacy.copy(journal = Journal()))
        val before = com.tinyblacksmith.core.persistence.SaveCodec.encodeRun(state)
        val changed = a.copy(coreId = b.id)
        val delta = engine.trayChange(state, a, changed, RecipeSlot.METAL, emptyList(), null)!!
        assertTrue(delta.changes.any { it.text.startsWith("Restock:") })
        assertTrue(delta.changes.any { it.text == "Metal + augment: untried" })
        assertNull(engine.forgeWorkbench(state, changed, emptyList()).command)
        assertNull(engine.forgeWorkbench(state, changed, emptyList()).outlook)
        assertEquals(before, com.tinyblacksmith.core.persistence.SaveCodec.encodeRun(state))
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
