package com.example.blacksmithproject

import com.example.blacksmithproject.GameSession.Op
import com.example.blacksmithproject.GameSession.Result
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.persistence.DayCursor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cursor row is a hint about one day: it is checked against the stored day and clamped to it, never trusted. */
@OptIn(ExperimentalCoroutinesApi::class)
class CursorTest : ShopDayTestBase() {

    @Test
    fun cursorOfAnotherDayIsIgnored() = vmTest {
        val day3 = stocked.nextDay().nextDay()
        val yesterday = stocked.nextDay().lastResolution!!.commandId.value
        for (stage in DayCursor.Stage.entries) {
            val repo = repo(day3)
            repo.cursor = DayCursor(yesterday, stage, index = 2).encode()
            val session = session(repo)
            val vm = open(repo, session = session)
            assertTrue("a $stage cursor of another day leaves the day unwatched", vm.day().resumed)
            assertEquals(0, vm.day().position.at)
            assertEquals(Result.DayNotWatched, session.run(Op.Dispatch(Command.BuyMaterial(LaunchContent.IRON), day3.runId)))
        }
        // A row that is not a cursor at all reads as no cursor.
        val garbled = repo(day3).also { it.cursor = "not a cursor" }
        assertEquals(0, open(garbled).day().position.at)
    }

    @Test
    fun aCursorPastTheEndOfTheDayIsClampedToIt() = vmTest {
        val day2 = stocked.nextDay()
        val id = day2.lastResolution!!.commandId.value
        fun at(cursor: DayCursor) = repo(day2).also { it.cursor = cursor.encode() }

        val counter = open(at(DayCursor(id, DayCursor.Stage.COUNTER, 999))).day().position
        assertEquals("the last card of the counter", Beat.ShopCloses, counter.beat)
        val negative = open(at(DayCursor(id, DayCursor.Stage.COUNTER, -5))).day().position
        assertEquals(0, negative.at)
        val ending = open(at(DayCursor(id, DayCursor.Stage.TOMORROW, 999))).day().position
        assertTrue(ending.isLast)
        // A stage this day has no cards for is the first card after it; every stored position is a card of the day.
        for (stage in listOf(DayCursor.Stage.COUNTER, DayCursor.Stage.AFTERMATH, DayCursor.Stage.TOMORROW)) for (i in 0..12) {
            val p = open(at(DayCursor(id, stage, i))).day().position
            assertTrue("$stage $i gave ${p.beat}", p.beat.stage >= stage)
        }
        // Each card's own cursor leads back to that card.
        counter.beats.indices.forEach { i -> assertEquals(i, ShopDayPosition.index(counter.beats, counter.copy(at = i).cursor(id))) }
    }

    @Test
    fun oldDismissedReportKeyIsPersistedAsDone() = vmTest {
        val day2 = fresh.nextDay()
        val id = day2.lastResolution!!.commandId.value

        // 0.6.0 left no cursor row; it kept the closed report's ID in settings.
        val closed = repo(day2)
        val settings = FakeSettings(dismissed = id)
        assertTrue(open(closed, settings).ui.value is UiState.Playing)
        assertEquals("handed over to the cursor row", DayCursor(id, DayCursor.Stage.DONE), closed.storedCursor())
        // Honoured once: with the cursor row in place the old key is never read again.
        val reads = settings.dismissedReads
        assertTrue(open(closed, settings).ui.value is UiState.Playing)
        assertEquals(reads, settings.dismissedReads)

        // A report that was never closed (the key names an earlier day, or nothing) opens as a day to resume.
        for (old in listOf(null, "some-earlier-day")) {
            val unread = repo(day2)
            val day = open(unread, FakeSettings(dismissed = old)).day()
            assertTrue(day.resumed && day.state.lastResolution == day2.lastResolution)
            assertNull(unread.storedCursor())
        }

        // An ended run whose report was closed in 0.6.0 opens on the run-end screen, unclaimed.
        val ended = lastEve.nextDay()
        val end = open(repo(ended), FakeSettings(dismissed = ended.lastResolution!!.commandId.value)).ui.value
        assertTrue("got $end", end is UiState.RunEnded && !end.claimed)
    }
}
