package com.example.blacksmithproject

import com.example.blacksmithproject.GameSession.Op
import com.example.blacksmithproject.GameSession.Result
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.shopday.ShopDay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The debug build's scenario saves as they are bundled (`src/debug/assets/scenarios/`, written by
 * `./gradlew :core:scenarios`), loaded the way the Scenarios menu loads them: through the session, over a run and a
 * legacy the player already has. Each then shows the mechanic it is listed for.
 */
class ScenarioAssetsTest {
    private val engine = GameEngine()
    private val dir = File("src/debug/assets/scenarios")
    private val index = Json.parseToJsonElement(File(dir, "index.json").readText()).jsonArray.map { it.jsonObject }

    /** A player with a legacy of their own and a run in progress. */
    private val mine = LegacyProfile(points = 12, upgrades = mapOf(LaunchContent.UPG_WALLS to 2), claimedRunIds = setOf("era1-seed99"))
    private val current: GameState = engine.newRun(mine, 42L)

    private fun endDay(s: GameState) = Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))

    private suspend fun TestScope.loaded(id: String, ownLegacy: Boolean): Pair<GameSession, FakeGameRepository> {
        val repo = FakeGameRepository(SaveCodec.encodeRun(current), SaveCodec.encodeLegacy(mine))
        val session = GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler))
        session.load()
        assertTrue(id, session.run(Op.LoadScenario(File(dir, "$id.json").readText(), ownLegacy, current.runId)) is Result.Done)
        return session to repo
    }

    @Test
    fun everyBundledScenarioLoadsOverARunAndOpensOnPlanning() = runTest {
        assertEquals("one file per index row, and the index", index.size + 1, dir.listFiles()!!.size)
        for (row in index) {
            val id = row.getValue("id").jsonPrimitive.content
            val ownLegacy = row.getValue("ownLegacy").jsonPrimitive.boolean
            val (session, repo) = loaded(id, ownLegacy)
            val snap = session.snapshot.value!!
            val run = snap.run!!
            assertTrue("$id replaced the run", run.runId != current.runId)
            assertEquals("$id: what is shown is what is stored", run, SaveCodec.decodeRun(repo.run!!))
            assertEquals("$id: the legacy row is the run's own copy", run.legacy, SaveCodec.decodeLegacy(repo.legacy!!))
            if (ownLegacy) assertTrue("$id brings its account", snap.legacy != mine && snap.legacy.eras.isNotEmpty())
            else assertEquals("$id keeps the player's legacy", mine, snap.legacy)
            assertEquals("$id opens on planning, not on yesterday's shop day", DayCursor.Stage.DONE, GameSession.pending(run, snap.legacy, snap.cursor))
            assertEquals(snap.cursor?.encode(), repo.cursor)
        }
    }

    @Test
    fun endDayOnABundledScenarioShowsItsCard() = runTest {
        for (row in index.filter { it.getValue("endDay").jsonPrimitive.boolean }) {
            val id = row.getValue("id").jsonPrimitive.content
            val (session, _) = loaded(id, row.getValue("ownLegacy").jsonPrimitive.boolean)
            val before = session.snapshot.value!!.run!!
            val done = session.run(Op.Dispatch(endDay(before), before.runId)) as Result.Done
            val script = ShopDay.script(done.accepted!!.resolution!!, done.accepted!!.state, engine.content, engine.config)
            row["card"]?.let { card -> assertTrue("$id: ${script.aftermath.map { it.kind }}", script.aftermath.any { it.kind.name == card.jsonPrimitive.content }) }
            if (id == "known_name") assertTrue(script.featured.any { it.customer?.regular == true && it.purchasedWeaponId != null })
        }
    }

    @Test
    fun theBundledLegendWakesUnderTheHone() = runTest {
        val (session, _) = loaded("returned_legend", ownLegacy = true)
        val run = session.snapshot.value!!.run!!
        val blade = run.storedWeapons().single { it.dormantAffixes.isNotEmpty() }
        val honed = session.run(Op.Dispatch(Command.Hone(blade.id), run.runId)) as Result.Done
        assertTrue(honed.accepted!!.events.any { it.type == EventType.WEAPON_HONED && "woke" in it.data })
        assertEquals(blade.dormantAffixes, honed.accepted!!.state.weapon(blade.id).affixes)
    }

    @Test
    fun aScenarioIssuedAgainstAnotherRunOrThatDoesNotDecodeWritesNothing() = runTest {
        val repo = FakeGameRepository(SaveCodec.encodeRun(current), SaveCodec.encodeLegacy(mine))
        val session = GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler))
        session.load()
        val text = File(dir, "hall_lesson.json").readText()
        assertEquals(Result.Stale, session.run(Op.LoadScenario(text, false, null)))
        assertTrue(session.run(Op.LoadScenario(text.dropLast(40), false, current.runId)) is Result.EngineFault)
        assertEquals(0, repo.commitCount)
        assertEquals(current.runId, session.snapshot.value?.run?.runId)
        assertNotNull(repo.run)
    }
}
