package com.example.blacksmithproject

import com.example.blacksmithproject.ui.forgeLearning
import com.example.blacksmithproject.ui.notebook
import com.example.blacksmithproject.ui.untriedPairing
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Journal as JournalRules
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Journal
import com.tinyblacksmith.core.model.KnowledgeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a forge taught, read from the journal before and after the accepted command; the notebook; the untried-pairing suggestion. */
class ForgeLearningTest : ShopDayTestBase() {
    private val ca = Journal.coreAugmentKey(forgeSword.coreId, forgeSword.augmentId)
    private val af = Journal.augmentFamilyKey(forgeSword.augmentId, forgeSword.familyId)

    /** The same recipe forged again and again, with the stock and the energy put back each time. */
    private fun forges(n: Int): List<Pair<GameState, GameState>> = buildList {
        var s = fresh
        repeat(n) {
            val before = s.copy(energy = 10, overworkToday = 0, materials = s.materials + (forgeSword.coreId to 5) + (forgeSword.augmentId to 5))
            val after = (engine.handle(before, forgeSword) as CommandOutcome.Accepted).state
            add(before to after)
            s = after
        }
    }

    @Test
    fun everyForgeSaysExactlyWhatTheJournalGainedAndNeverMore() {
        val titles = mutableSetOf<String>()
        val notes = mutableSetOf<String>()
        for ((before, after) in forges(8)) {
            val was = before.legacy.journal
            val now = after.legacy.journal
            val learned = engine.forgeLearning(was, now, forgeSword)
            val moved = listOf(ca, af).filter { now.state(it).ordinal > was.state(it).ordinal }
            val pairings = learned.changes.filter { it.title == "First notes" || it.title == "Pairing learned" }
            assertEquals(moved.size, pairings.size)
            moved.zip(pairings).forEach { (key, change) ->
                assertEquals(if (now.state(key) == KnowledgeState.OBSERVED) "First notes" else "Pairing learned", change.title)
                // The words are the journal's own for the stage reached: an observed pairing stays tentative.
                assertEquals("${JournalRules.subjectName(engine.content, key)} · ${JournalRules.hint(now, engine.content, key)}", change.line)
                if (now.state(key) == KnowledgeState.OBSERVED) assertTrue(change.line, change.line.contains("· Seems "))
            }
            if (learned.changes.isEmpty()) assertNotNull(learned.note) else assertNull(learned.note)
            if (learned.changes.isEmpty() && listOf(ca, af).any { now.state(it) == KnowledgeState.OBSERVED }) assertEquals("Experiment recorded. Try this pairing again to learn more.", learned.note)
            titles += learned.changes.map { it.title }
            learned.note?.let { notes += it }
        }
        assertTrue("a first forge is first notes", "First notes" in titles)
        assertTrue("repeating reaches understanding", "Pairing learned" in titles)
        assertTrue("a known recipe teaches nothing and says so", "No new findings. You've already learned these pairings." in notes)
    }

    @Test
    fun theNotebookHoldsOnlyWhatWasTriedAndSortsItByKind() {
        assertTrue(engine.notebook(fresh.legacy.journal).let { it.metal.isEmpty() && it.weapon.isEmpty() && it.clues.isEmpty() })
        val journal = forges(1).single().second.legacy.journal
        val book = engine.notebook(journal)
        assertEquals(listOf(ca), book.metal.map { it.key })
        assertEquals(listOf(af), book.weapon.map { it.key })
        assertEquals(journal.interactions.keys.count { it.startsWith("sig:") }, book.clues.size)
        assertEquals("2 being studied · 0 learned", book.counts)
        assertEquals(forgeSword.coreId to forgeSword.augmentId, book.metal.single().let { it.metalId to it.augmentId })
        assertEquals(forgeSword.familyId, book.weapon.single().familyId)
    }

    @Test
    fun anUntriedPairingIsTheFirstInStockOneTheJournalDoesNotKnow() {
        val cores = engine.content.materials(MaterialCategory.CORE)
        val augments = engine.content.materials(MaterialCategory.AUGMENT)
        fun GameState.owns(id: com.tinyblacksmith.core.model.MaterialId) = (materials[id] ?: 0) > 0
        val first = engine.untriedPairing(fresh)!!
        assertEquals(cores.first { fresh.owns(it.id) }.id to augments.first { fresh.owns(it.id) }.id, first)
        // Once the journal knows it, the suggestion moves on; with every stocked pairing known there is none.
        val knows = fresh.copy(legacy = fresh.legacy.copy(journal = Journal(interactions = mapOf(Journal.coreAugmentKey(first.first, first.second) to KnowledgeState.OBSERVED))))
        assertTrue(engine.untriedPairing(knows) != first)
        val all = cores.flatMap { c -> augments.map { a -> Journal.coreAugmentKey(c.id, a.id) to KnowledgeState.UNDERSTOOD } }.toMap()
        assertNull(engine.untriedPairing(fresh.copy(legacy = fresh.legacy.copy(journal = Journal(interactions = all)))))
        assertNull(engine.untriedPairing(fresh.copy(materials = emptyMap())))
    }
}
