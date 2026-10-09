package com.example.blacksmithproject

import com.example.blacksmithproject.GameSession.Op
import com.example.blacksmithproject.GameSession.Result
import com.example.blacksmithproject.GameSession.Status
import com.example.blacksmithproject.data.ShopDaySpeed
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The shop day is a way of looking at a day that is already saved: however it is watched, skipped, interrupted or
 * failed at, the stored game is the one End Day committed (plan 6.5, S06).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresentationEqualityTest : ShopDayTestBase() {

    private fun TestScope.skipAndBegin(vm: GameViewModel) {
        vm.skipDay(); advanceUntilIdle()
        if (vm.day().position.beat == Beat.Blessing) { vm.decideLater(); advanceUntilIdle() }
        vm.acknowledge(); advanceUntilIdle()
    }

    @Test
    fun watchingSkippingAndRestartingLeaveIdenticalRunAndLegacyBytes() = vmTest {
        for (eve in listOf(stocked, blessingEve, lastEve)) {
            // One committed End Day; then three ways through its presentation.
            val watched = planning(eve)
            val first = open(watched)
            first.endDay(); advanceUntilIdle()
            val run = watched.run
            val legacy = watched.legacy
            assertNotEquals("the day was committed", SaveCodec.encodeRun(eve), run)
            assertEquals(1, watched.commitCount)
            val skipped = watched.twin()
            val restarted = watched.twin()

            // Watched card by card, there and back, with the Gazette read on the way.
            first.next(); advanceUntilIdle()
            first.openGazette(); assertTrue(first.back()); advanceUntilIdle()
            assertTrue(first.back()); advanceUntilIdle()
            watchToTheEnd(first)
            first.acknowledge(); advanceUntilIdle()

            skipAndBegin(open(skipped))

            // Killed after the first card, again mid-way, and once more on the last card.
            open(restarted).let { vm -> vm.resumeDay(); vm.next(); advanceUntilIdle() }
            open(restarted).let { vm -> assertTrue(vm.day().resumed); vm.resumeDay(); advanceUntilIdle(); watchToTheEnd(vm) }
            open(restarted).let { vm -> assertTrue(vm.day().position.isLast); vm.resumeDay(); vm.acknowledge(); advanceUntilIdle() }

            for (store in listOf(watched, skipped, restarted)) {
                assertEquals("the run row is the one End Day committed", run, store.run)
                assertEquals(legacy, store.legacy)
                assertEquals("nothing but the cursor was written after End Day", if (store === watched) 1 else 0, store.commitCount)
                assertEquals(DayCursor.Stage.DONE, store.storedCursor()?.stage)
            }
            if (eve === lastEve) continue
            // And the next day comes out the same on all three.
            val next = listOf(watched, skipped, restarted).map { store -> open(store).also { it.endDay(); advanceUntilIdle() }; store.run }
            assertNotEquals(run, next[0])
            assertEquals(next[0], next[1])
            assertEquals(next[0], next[2])
        }
    }

    @Test
    fun noCommandOtherThanChooseBlessingIsIssuedByTheSequence() = vmTest {
        val repo = planning(blessingEve)
        val session = session(repo)
        // Every op that reaches the engine or the save raises Working; cursor moves do not.
        val issued = mutableListOf<Op>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session.status.collect { (it as? Status.Working)?.let { w -> issued += w.op } } }
        val vm = open(repo, session = session)
        vm.endDay(); advanceUntilIdle()
        val run = repo.run
        issued.clear()

        // Everything the sequence can do short of choosing: every card, back, the Gazette, a sheet, speed, skip, decide later.
        vm.next(); advanceUntilIdle()
        vm.back(); advanceUntilIdle()
        vm.openGazette(); vm.closeGazette()
        vm.openSheet(Sheet.Hero(vm.day().state.heroes.keys.first())); vm.closeSheet()
        vm.setSpeed(ShopDaySpeed.X2); advanceUntilIdle()
        assertEquals(ShopDaySpeed.X2, vm.day().speed)
        watchToTheEnd(vm)
        assertEquals(Beat.Tomorrow, vm.day().position.beat)
        vm.back(); advanceUntilIdle()
        assertEquals(Beat.Blessing, vm.day().position.beat)
        vm.skipDay(); vm.resumeDay(); advanceUntilIdle()
        assertEquals(emptyList<Op>(), issued)
        assertEquals(1, repo.commitCount)
        assertEquals(run, repo.run)

        // The one exception.
        val blessing = vm.day().state.pendingBlessingOffer.first()
        vm.chooseBlessing(blessing); advanceUntilIdle()
        assertEquals(listOf<Op>(Op.Dispatch(Command.ChooseBlessing(blessing), blessingEve.runId)), issued)
        assertEquals(2, repo.commitCount)
        assertEquals(Beat.Tomorrow, vm.day().position.beat)
        vm.acknowledge(); advanceUntilIdle()
        assertEquals(1, issued.size)
        assertEquals(2, repo.commitCount)
    }

    @Test
    fun blessingChosenInTheSequenceEqualsChosenNextMorning() = vmTest {
        val inSequence = planning(blessingEve)
        val nextMorning = inSequence.twin()
        val blessing = blessingEve.nextDay().pendingBlessingOffer.first()

        val a = open(inSequence)
        a.endDay(); advanceUntilIdle()
        a.skipDay(); advanceUntilIdle()
        assertEquals("Skip day lands on the blessing", Beat.Blessing, a.day().position.beat)
        assertEquals(DayCursor.Stage.TOMORROW, inSequence.storedCursor()?.stage)
        a.chooseBlessing(blessing); advanceUntilIdle()
        assertEquals("the lead no longer asks for a choice that was made", Beat.Tomorrow, a.day().position.beat)
        assertTrue(a.day().state.pendingBlessingOffer.isEmpty())
        a.acknowledge(); advanceUntilIdle()

        val b = open(nextMorning)
        b.endDay(); advanceUntilIdle()
        b.skipDay(); advanceUntilIdle()
        b.decideLater(); advanceUntilIdle()
        b.acknowledge(); advanceUntilIdle()
        assertEquals("the offer waits in the saved game", listOf(blessing), b.playing().state.pendingBlessingOffer.take(1))
        b.dispatch(Command.ChooseBlessing(blessing)); advanceUntilIdle()

        assertEquals(inSequence.run, nextMorning.run)
        assertEquals(inSequence.legacy, nextMorning.legacy)
        assertTrue(SaveCodec.decodeRun(inSequence.run!!).blessings.any { it.id == blessing })
        // The days that follow are the same days.
        a.endDay(); b.endDay(); advanceUntilIdle()
        assertEquals(blessingEve.day + 2, a.day().state.day)
        assertEquals(inSequence.run, nextMorning.run)
    }

    @Test
    fun aLostCursorWriteOnlyRepeatsAVisit() = vmTest {
        val repo = repo(stocked)
        val vm = open(repo)
        vm.endDay(); advanceUntilIdle()
        val run = repo.run
        assertTrue("the day has a customer to repeat", vm.day().script.featured.isNotEmpty())
        vm.next(); advanceUntilIdle()
        val reached = vm.day().position
        assertEquals(Beat.Visit(0), reached.beat)
        assertEquals(reached.cursor(repo.storedCursor()!!.commandId), repo.storedCursor())

        // The write behind the next card is lost; the player sees the card anyway.
        repo.failNextCursor = IOException("disk full")
        vm.next(); advanceUntilIdle()
        assertEquals(reached.at + 1, vm.day().position.at)
        assertEquals(Status.Idle, vm.day().op)

        // After a kill the day resumes one card earlier, and that is all it costs.
        val again = open(repo)
        assertTrue(again.day().resumed)
        assertEquals(reached, again.day().position)
        assertEquals(run, repo.run)
        again.resumeDay(); advanceUntilIdle()
        watchToTheEnd(again)
        again.acknowledge(); advanceUntilIdle()
        assertEquals(stocked.day + 1, again.playing().state.day)
        assertEquals(run, repo.run)
    }

    @Test
    fun planningIsRefusedImmediatelyAfterTheCommitWithAStaleCursor() = vmTest {
        // Day 2, its presentation acknowledged: the cursor row names yesterday and says DONE.
        val day2 = fresh.nextDay()
        val repo = repo(day2)
        val stale = DayCursor(day2.lastResolution!!.commandId.value, DayCursor.Stage.DONE)
        repo.cursor = stale.encode()
        val session = session(repo)
        val vm = open(repo, session = session)
        assertEquals(2, vm.playing().state.day)

        assertTrue(session.run(Op.Dispatch(endDay(day2), day2.runId)) is Result.Done)
        // Nothing has moved the cursor yet: it still says DONE, for the day before.
        assertEquals(stale, repo.storedCursor())
        assertEquals(stale, session.snapshot.value?.cursor)
        val gold = session.snapshot.value!!.run!!.gold
        assertEquals(Result.DayNotWatched, session.run(Op.Dispatch(Command.BuyMaterial(LaunchContent.IRON), day2.runId)))
        assertEquals(Result.DayNotWatched, session.run(Op.Dispatch(endDay(session.snapshot.value!!.run!!), day2.runId)))
        vm.dispatch(Command.BuyMaterial(LaunchContent.IRON)); vm.endDay(); advanceUntilIdle()
        assertEquals(3, vm.day().state.day)
        assertEquals(gold, vm.day().state.gold)
        assertEquals("one End Day, nothing else", 1, repo.commitCount)
        assertEquals("the new day starts at its first card", 0, vm.day().position.at)
        assertFalse(vm.day().resumed)

        // A cursor move issued for the day before arrives too late to count for this one.
        assertEquals(Result.Stale, session.run(Op.MoveCursor(stale)))
        assertEquals(Result.DayNotWatched, session.run(Op.Dispatch(Command.BuyMaterial(LaunchContent.IRON), day2.runId)))
    }

    @Test
    fun aFailingCursorRowNeverLocksTheBlessingOrTheNextDay() = vmTest {
        val repo = planning(blessingEve)
        val stale = repo.cursor
        repo.failEveryCursor = IOException("the cursor row cannot be written")
        val vm = open(repo)
        vm.endDay(); advanceUntilIdle()
        vm.next(); advanceUntilIdle()
        vm.skipDay(); advanceUntilIdle()
        assertEquals(Beat.Blessing, vm.day().position.beat)
        assertEquals("no cursor write ever landed", stale, repo.cursor)

        val blessing = vm.day().state.pendingBlessingOffer.first()
        vm.chooseBlessing(blessing); advanceUntilIdle()
        assertTrue("the blessing was taken and saved", SaveCodec.decodeRun(repo.run!!).blessings.any { it.id == blessing })
        assertNull(vm.day().lastError)
        vm.acknowledge(); advanceUntilIdle()
        val day = vm.playing().state.day
        assertEquals(blessingEve.day + 1, day)
        val gold = vm.playing().state.gold
        vm.dispatch(Command.BuyMaterial(LaunchContent.IRON)); advanceUntilIdle()
        assertTrue("planning is open", vm.playing().state.gold < gold)
        vm.endDay(); advanceUntilIdle()
        assertEquals(day + 1, vm.day().state.day)
        assertEquals(stale, repo.cursor)

        // What it costs: opened again, the game offers to resume a day that was already watched. It is never locked.
        val again = open(repo)
        assertTrue(again.day().resumed)
        again.skipDay(); advanceUntilIdle()
        if (again.day().position.beat == Beat.Blessing) { again.decideLater(); advanceUntilIdle() }
        again.acknowledge(); advanceUntilIdle()
        assertEquals(day + 1, again.playing().state.day)
    }
}
