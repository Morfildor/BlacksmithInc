package com.example.blacksmithproject

import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.shopUi
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.model.HistoryEntry
import com.tinyblacksmith.core.model.WeaponSnapshot
import com.tinyblacksmith.core.shopday.Lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The blade sheet's Story and the dormant marker: `Lines.story` and `Lines.dormant`, and nothing asleep is told as a buff. */
class StoryModelTest : ShopDayTestBase() {
    private val blade get() = stocked.listedWeapons().first()
    private val buffs get() = engine.content.affixes.filter { it.kind == AffixKind.BENEFICIAL }

    @Test
    fun theSheetTellsTheStoryCoreKeeps() {
        // A blade from an earlier era with routine fights between the entries worth telling.
        val history = listOf(
            HistoryEntry(1, 2, "FORGED", "Forged in the first era."),
            HistoryEntry(1, 3, "EQUIPPED", "Taken up by a hero."),
            HistoryEntry(1, 4, "VICTORY", "Won its first fight."),
            HistoryEntry(1, 5, "VICTORY", "Won again."),
            HistoryEntry(stocked.era, 1, "RETURNED", "Came back to the forge."),
        )
        val old = blade.copy(history = history)
        val state = stocked.copy(weapons = stocked.weapons + (old.id to old))
        val detail = engine.itemDetail(state, old.id)!!
        assertEquals(Lines.story(old, state.era), detail.story)
        assertTrue("an entry of the era being played is dated by its day alone", detail.story.last().startsWith("Day 1: "))
        assertFalse("the second routine victory is not part of the story", detail.story.any { it.endsWith("Won again.") })
        assertEquals("the history under it still holds every record", history.size, detail.history.size)
        // A blade that has left the save opens from its snapshot: there is no ledger to tell.
        assertTrue(engine.itemDetail(stocked.copy(weapons = stocked.weapons - old.id), old.id, WeaponSnapshot.of(old))!!.story.isEmpty())
    }

    @Test
    fun dormantAffixesAreMarkedAndAreNotBuffs() {
        val awake = buffs[0]
        val asleep = listOf(buffs[1], buffs[2])
        val legend = blade.copy(affixes = listOf(awake.id), dormantAffixes = asleep.map { it.id })
        val state = stocked.copy(weapons = stocked.weapons + (legend.id to legend))
        val detail = engine.itemDetail(state, legend.id)!!
        val line = Lines.dormant(legend.dormantAffixes, engine.content)!!
        assertEquals(line, detail.dormant)
        assertEquals(listOf(awake.name), detail.affixes.map { it.name })
        // The stock row says the same line and lists only what is awake.
        val row = engine.shopUi(state).shelf.first { it.weapon.id == legend.id }
        assertEquals(line, row.dormant)
        assertEquals(listOf(awake.name), row.buffs)
        // A snapshot carries the sleeping affixes too; a blade with none says nothing.
        assertEquals(line, engine.itemDetail(stocked.copy(weapons = stocked.weapons - legend.id), legend.id, WeaponSnapshot.of(legend))!!.dormant)
        assertNull(engine.itemDetail(stocked, blade.id)!!.dormant)
        assertNull(engine.shopUi(stocked).shelf.first().dormant)
    }
}
