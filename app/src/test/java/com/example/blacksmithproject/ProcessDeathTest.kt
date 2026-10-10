package com.example.blacksmithproject

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The process can die at any moment (plan 6.5). A death is a new session and ViewModel over the same stored rows: the
 * day is then either not resolved at all or resolved exactly as it was, and the presentation goes on from its saved card.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProcessDeathTest : ShopDayTestBase() {

    @Test
    fun killBeforeCommitThenEndDayAgainGivesTheSameDay() = vmTest {
        // The day as an undisturbed process resolves it.
        val reference = repo(stocked)
        open(reference).also { it.endDay(); advanceUntilIdle() }

        // This process computed the day and died before its transaction landed: the store still holds the morning.
        val repo = repo(stocked)
        val morning = repo.run
        repo.hold = true
        val dying = open(repo)
        dying.endDay(); advanceUntilIdle()
        assertTrue("the commit is in flight", dying.playing().busy)
        assertEquals("nothing is shown before it is saved", stocked.day, dying.playing().state.day)
        assertEquals(morning, repo.run)
        val left = repo.twin()          // what the dead process left on disk

        val again = open(left)
        assertEquals(stocked.day, again.playing().state.day)
        again.endDay(); advanceUntilIdle()
        assertEquals("the same day, to the byte", reference.run, left.run)
        assertEquals(reference.legacy, left.legacy)
        assertEquals(reference.let { open(it).day().script }, again.day().script)
        // Had the first process lived, its commit would have stored that same day.
        repo.releaseAll(); advanceUntilIdle()
        assertEquals(reference.run, repo.run)
    }

    @Test
    fun killAroundAForgeCommitConsumesMaterialsOnceAndTheBladeIsFoundAfterRelaunch() = vmTest {
        val iron = fresh.materials[LaunchContent.IRON] ?: 0
        val reference = repo(fresh)
        open(reference).also { it.dispatch(forgeSword); advanceUntilIdle() }
        val forged = SaveCodec.decodeRun(reference.run!!)
        assertEquals(1, forged.weapons.size)
        assertEquals(iron - 1, forged.materials[LaunchContent.IRON] ?: 0)

        // Killed before the forge commit: nothing was consumed and nothing was made; forging again makes the same blade.
        val before = repo(fresh)
        before.hold = true
        open(before).also { it.dispatch(forgeSword); advanceUntilIdle() }
        assertEquals(SaveCodec.encodeRun(fresh), before.run)
        val left = before.twin()
        before.releaseAll(); advanceUntilIdle()
        val again = open(left)
        assertTrue(again.playing().state.weapons.isEmpty())
        assertEquals(iron, again.playing().state.materials[LaunchContent.IRON] ?: 0)
        again.dispatch(forgeSword); advanceUntilIdle()
        assertEquals(reference.run, left.run)

        // Killed after the commit (the reveal was never seen): the blade is in storage, paid for once.
        val relaunched = open(reference.twin())
        assertEquals(forged.weapons.keys, relaunched.playing().state.weapons.keys)
        assertTrue(relaunched.playing().state.weapons.values.single().isInStorage)
        assertEquals(iron - 1, relaunched.playing().state.materials[LaunchContent.IRON] ?: 0)
        assertEquals(forged.energy, relaunched.playing().state.energy)
    }

    @Test
    fun killAfterCommitResumesAtTheFirstBeat() = vmTest {
        val repo = repo(stocked)
        val first = open(repo)
        first.endDay(); advanceUntilIdle()
        assertFalse("the day just ended needs no prompt", first.day().resumed)
        val run = repo.run
        val script = first.day().script

        val again = open(repo)
        val day = again.day()
        assertTrue("the Resume prompt", day.resumed)
        assertEquals(0, day.position.at)
        assertEquals(Beat.ShopOpens, day.position.beat)
        assertEquals(stocked.day + 1, day.state.day)
        assertEquals("the same day is shown, not a new one", script, day.script)
        assertEquals(run, repo.run)
        assertEquals(1, repo.commitCount)
        // The prompt itself moves nothing; Resume goes on from the first card.
        again.next(); advanceUntilIdle()
        assertEquals(0, again.day().position.at)
        again.resumeDay(); again.next(); advanceUntilIdle()
        assertEquals(false to 1, again.day().resumed to again.day().position.at)
    }

    @Test
    fun killMidCounterResumesAtTheStoredVisit() = vmTest {
        val repo = repo(stocked)
        val first = open(repo)
        first.endDay(); advanceUntilIdle()
        assertTrue("the day has two customers at the counter: ${first.day().position.beats}", first.day().script.featured.size >= 2)
        first.next(); first.next(); advanceUntilIdle()
        assertEquals(Beat.Visit(1), first.day().position.beat)
        val run = repo.run

        val again = open(repo)
        assertTrue(again.day().resumed)
        assertEquals(Beat.Visit(1), again.day().position.beat)
        assertEquals(DayCursor.Stage.COUNTER, repo.storedCursor()?.stage)
        assertEquals(first.day().script, again.day().script)
        assertEquals(run, repo.run)

        // Stepping back before the kill does not move the saved position back: it is the furthest card reached.
        again.resumeDay(); assertTrue(again.back()); advanceUntilIdle()
        assertEquals(Beat.Visit(0), again.day().position.beat)
        assertEquals(Beat.Visit(1), open(repo).day().position.beat)

        // Skip to tomorrow from the prompt lands on the ending, and a kill there reopens on it.
        val skipping = open(repo)
        skipping.skipDay(); advanceUntilIdle()
        assertEquals(false to DayCursor.Stage.TOMORROW, skipping.day().resumed to skipping.day().position.beat.stage)
        assertEquals(DayCursor.Stage.TOMORROW, open(repo).day().position.beat.stage)
        assertEquals(run, repo.run)
    }

    /** The summary of the day's fights is a card like any other: a kill on it reopens on it, with the same words and the same save. */
    @Test
    fun killOnTheFieldSummaryReopensOnIt() = vmTest {
        val repo = repo(fresh)
        val first = open(repo)
        first.endDay(); advanceUntilIdle()
        first.next(); advanceUntilIdle()
        assertEquals(Beat.Aftermath(0), first.day().position.beat)
        assertEquals(com.tinyblacksmith.core.shopday.AftermathKind.FIELD_SUMMARY, first.day().script.aftermath.single().kind)
        val run = repo.run

        val again = open(repo)
        assertTrue(again.day().resumed)
        assertEquals(Beat.Aftermath(0), again.day().position.beat)
        assertEquals(DayCursor.Stage.AFTERMATH, repo.storedCursor()?.stage)
        assertEquals(first.day().script, again.day().script)
        assertEquals(first.day().model, again.day().model)
        assertEquals(run, repo.run)
    }

    @Test
    fun acknowledgedDayOpensPlanningOnTheNextDay() = vmTest {
        val repo = repo(stocked)
        val first = open(repo)
        first.endDay(); advanceUntilIdle()
        watchToTheEnd(first)
        first.acknowledge(); advanceUntilIdle()
        assertEquals(Dest.SHOP, first.playing().dest)
        val run = repo.run

        val again = open(repo)
        assertEquals(stocked.day + 1, again.playing().state.day)
        assertEquals(run, repo.run)
        again.endDay(); advanceUntilIdle()
        assertEquals(stocked.day + 2, again.day().state.day)
        assertEquals(0, again.day().position.at)
    }

    @Test
    fun defeatDayResumesThenEndsTheRun() = vmTest {
        val repo = planning(lastEve)
        val first = open(repo)
        first.endDay(); advanceUntilIdle()
        assertTrue(first.day().state.isEnded)
        val run = repo.run

        // Killed on the day the forge fell: the day is shown first, not the run-end screen.
        val again = open(repo)
        assertTrue(again.day().resumed)
        assertEquals(Beat.Fallen, again.day().position.beats.last())
        again.claimLegacy(); advanceUntilIdle()
        assertTrue("no claim before the day is watched", (again.ui.value as UiState.ShopDay).lastError != null)
        again.skipDay(); advanceUntilIdle()
        assertEquals(Beat.Fallen, again.day().position.beat)

        // Killed on Forge fallen: it reopens there; "See the legacy" ends the run.
        val fallen = open(repo)
        assertEquals(Beat.Fallen, fallen.day().position.beat)
        fallen.resumeDay(); fallen.acknowledge(); advanceUntilIdle()
        val end = fallen.ui.value as UiState.RunEnded
        assertFalse(end.claimed)
        assertEquals(run, repo.run)
        assertTrue("and stays ended in a new process", open(repo).ui.value is UiState.RunEnded)
        fallen.claimLegacy(); advanceUntilIdle()
        assertTrue((fallen.ui.value as UiState.RunEnded).claimed)
        assertEquals("the ended run is kept", run, repo.run)
        assertTrue((open(repo).ui.value as UiState.RunEnded).claimed)
    }
}
