package com.example.blacksmithproject

import androidx.lifecycle.SavedStateHandle
import com.example.blacksmithproject.ui.Tips
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.persistence.DayCursor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What comes back when the system has killed the process and hands over its saved state: the store, the saved-state
 * values and the settings, and nothing in memory. The device twin over a real Room file is `ShopDayPersistenceTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LifecycleTest : ShopDayTestBase() {
    private fun SavedStateHandle.kept() = SavedStateHandle(keys().associateWith { get<Any?>(it) })

    /** Killed while the day is watched: the same day at the card reached, read from the store and not simulated again; then planning as it was left. */
    @Test
    fun aProcessKilledInTheShopDayComesBackToTheDayAndThenToThePlanningItLeft() = vmTest {
        val store = repo(stocked)
        val handle = SavedStateHandle()
        val first = open(store, saved = handle)
        val draft = ForgeDraft(ForgeMode.ADVANCED, LaunchContent.BOW, LaunchContent.IRON, LaunchContent.FROST_BLOOM, null, Risk.SAFE, Technique.TEMPER)
        first.selectRecords(RecordsPage.JOURNAL); first.selectDest(Dest.FORGE); first.updateDraft { draft }
        first.endDay(); advanceUntilIdle()
        first.next(); first.next(); advanceUntilIdle()
        val before = first.day()
        assertEquals(2, before.position.at)
        val commits = store.commitCount
        val run = store.run

        val again = open(store, saved = handle.kept())
        val day = again.day()
        assertTrue("opened onto an unwatched day: the Resume prompt", day.resumed)
        assertEquals(before.position, day.position)
        assertEquals(before.script, day.script)
        assertEquals(before.model, day.model)
        assertEquals("the day was read, not resolved again", commits to run, store.commitCount to store.run)

        again.resumeDay(); advanceUntilIdle()
        watchToTheEnd(again)
        again.acknowledge(); advanceUntilIdle()
        val planning = again.playing()
        assertEquals("a watched day always ends on the Shop", Dest.SHOP, planning.dest)
        assertEquals(RecordsPage.JOURNAL, planning.records)
        assertEquals(draft, planning.draft)
        assertEquals(DayCursor.Stage.DONE, store.storedCursor()?.stage)

        // Killed once more, on planning: the next day, with the same draft, and no day to watch.
        val third = open(store, saved = handle.kept())
        assertEquals(planning.state, third.playing().state)
        assertEquals(draft, third.playing().draft)
        assertEquals(commits, store.commitCount)
    }

    /** A dismissed tip lives in settings, not in the process: it stays dismissed for the next process and across runs. */
    @Test
    fun aSeenTipStaysSeenForTheNextProcess() = vmTest {
        val settings = FakeSettings()
        val store = repo(fresh)
        val first = open(store, settings)
        assertFalse(Tips.COUNTER.id in settings.seenTips.value)
        first.dismissTip(Tips.COUNTER.id); advanceUntilIdle()

        open(store, settings)
        assertEquals(setOf(Tips.COUNTER.id), settings.seenTips.value)
        // Nothing of it is in the save: the stored run is the run that was loaded.
        assertEquals(com.tinyblacksmith.core.persistence.SaveCodec.encodeRun(fresh), store.run)
    }
}
