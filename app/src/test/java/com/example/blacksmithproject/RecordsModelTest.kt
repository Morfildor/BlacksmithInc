package com.example.blacksmithproject

import com.example.blacksmithproject.ui.gazetteMarks
import com.example.blacksmithproject.ui.legendUi
import com.example.blacksmithproject.ui.signatureUi
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.crafting.ClueRung
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.HistoryEntry
import com.tinyblacksmith.core.model.Journal as JournalModel
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.LegendEntry
import com.tinyblacksmith.core.shopday.Lines
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records: a Legend Board entry in the lines of `Lines.legend`, a signature's clue ladder, and "Use this recipe". */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsModelTest : ShopDayTestBase() {
    // A signature with a catalyst and a temper, so every rung has something of its own to say.
    private val def = SignatureCatalog.all.first { it.catalystId != null && it.risk != null }

    @Test
    fun aLegendIsToldInCoresLinesAndAnOldOneSaysItIsLostToTime() {
        val buff = engine.content.affixes.first { it.kind == AffixKind.BENEFICIAL }
        val kept = LegendEntry(
            1, "Iron Sword", "Wallkeeper", kills = 9, fame = 6, owners = listOf("Mira Ashwood", "Tam Reed"), affixes = listOf(buff.id),
            ownerLine = listOf(HistoryEntry(1, 4, "SOLD", "Sold to Mira Ashwood for 60 gold.")), weaponKey = "era1-w3",
        )
        val told = legendUi(kept, engine.content, currentEra = 2)
        assertEquals(Lines.legend(kept, engine.content, 2), listOf(told.head) + told.lines)
        assertFalse(told.lostToTime)
        assertTrue(told.head.startsWith("Iron Sword, Wallkeeper."))
        assertTrue(told.lines.any { buff.name in it })
        assertEquals("Era 1, day 4: Sold to Mira Ashwood for 60 gold.", told.lines.last())

        // An entry from a profile older than the record of a blade's make: only its name, fame and kills.
        val old = LegendEntry(1, "Bronze Axe", "Old Faithful", kills = 4, fame = 3, owners = emptyList())
        val lost = legendUi(old, engine.content, currentEra = 2)
        assertTrue(lost.lostToTime)
        assertEquals("Its properties are lost to time.", lost.lines.first())
        assertEquals(Lines.legend(old, engine.content, 2), listOf(lost.head) + lost.lines)
    }

    @Test
    fun aSignaturesLadderShowsTheRungsEarnedAndNothingElse() {
        // Two rungs earned: the base recipe and the catalyst.
        val partial = JournalModel(interactions = mapOf(def.journalKey to KnowledgeState.OBSERVED), signatureClues = mapOf(def.journalKey to (ClueRung.RECIPE.bit or ClueRung.CATALYST.bit)))
        val ui = signatureUi(partial, def.journalKey, engine.content, engine.config)!!
        assertEquals(listOf("Recipe", "Catalyst", "Temper", "Finish"), ui.rungs.map { it.label })
        assertEquals(Journal.rungs(partial, def), ClueRung.entries.filterIndexed { i, _ -> ui.rungs[i].clue != null }.toSet())
        assertEquals(listOf(Journal.clue(def, ClueRung.RECIPE, engine.config), Journal.clue(def, ClueRung.CATALYST, engine.config), null, null), ui.rungs.map { it.clue })
        assertNull("an unfound signature offers no recipe", ui.recipe)
        assertFalse("its name stays hidden", ui.rungs.any { it.clue?.contains(def.name) == true })

        // Found: every rung is told, the first as the recipe itself, and the forge it asks for is offered.
        val found = JournalModel(interactions = mapOf(def.journalKey to KnowledgeState.SIGNATURE_DISCOVERED))
        val known = signatureUi(found, def.journalKey, engine.content, engine.config)!!
        assertTrue(known.rungs.all { it.clue != null })
        assertEquals(Journal.subjectName(engine.content, def.journalKey), known.rungs.first().clue)
        assertEquals(SignatureCatalog.recipe(def), known.recipe)

        // A pairing's row is not a signature's.
        assertNull(signatureUi(found, JournalModel.coreAugmentKey(def.coreId, def.augmentId), engine.content, engine.config))
    }

    @Test
    fun useThisRecipeFillsTheForgeDraft() = vmTest {
        val vm = open(repo(fresh))
        val recipe = SignatureCatalog.recipe(def)
        vm.updateDraft { it.copy(technique = com.tinyblacksmith.core.engine.Technique.ETCH, commissionId = CommissionId("gone")) }
        vm.useRecipe(recipe); advanceUntilIdle()
        val p = vm.playing()
        assertEquals(Dest.FORGE, p.dest)
        val d = p.draft
        assertNotNull(d.familyId)
        // The Forge button would issue exactly the recipe.
        assertEquals(recipe, Command.Forge(d.mode, d.familyId!!, d.coreId!!, d.augmentId!!, d.catalystId, d.risk, d.technique))
        assertNull(d.commissionId)
        assertEquals("nothing was forged: the save is the same", fresh, p.state)
    }

    @Test
    fun anArchiveRowMarksASiegeAndADeathFromTheRecordTypes() {
        assertEquals(listOf("⚔ Siege"), gazetteMarks(listOf(EventType.WEAPON_SOLD, EventType.SIEGE_WON)))
        assertEquals(listOf("⚔ Siege", "† Death"), gazetteMarks(listOf(EventType.SIEGE_LOST, EventType.HERO_DIED)))
        assertEquals(listOf("† Death"), gazetteMarks(listOf(EventType.HERO_DIED)))
        // A warning of a siege or a wound is neither.
        assertEquals(emptyList<String>(), gazetteMarks(listOf(EventType.SIEGE_WARNING, EventType.HERO_WOUNDED, EventType.EXPEDITION_LOST)))
    }
}
