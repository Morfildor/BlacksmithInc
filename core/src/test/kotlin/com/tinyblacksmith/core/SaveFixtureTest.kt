package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Phase
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Regression anchor for the save schema: a real mid-run v1 save captured with the codec as it stood on 2026-10-08
 * (forced survival, seed 4242, BALANCED_FAIR, 60 End Days). Every later schema change must still decode it,
 * migrate it and leave it playable. Byte-equality against the fixture is deliberately NOT asserted: fields added
 * with defaults change the encoded text (`encodeDefaults = true`) without changing the decoded state.
 */
class SaveFixtureTest {
    private companion object {
        const val FIXTURE = "/saves/v1_forced_seed4242_day61.json"
        val combatKinds = setOf("VICTORY", "SIEGE")

        val fixtureText: String by lazy { SaveFixtureTest::class.java.getResource(FIXTURE)?.readText() ?: error("missing $FIXTURE") }
        val decoded: GameState by lazy { SaveCodec.decodeRun(fixtureText) }
    }

    @Test
    fun fixtureIsAVersionOneEnvelope() {
        assertTrue(fixtureText.startsWith("""{"schemaVersion":1,"payload":"""), fixtureText.take(40))
    }

    @Test
    fun decodesToTheCapturedMidRunState() {
        val s = decoded
        assertEquals(4242L, s.seed)
        assertEquals("era1-seed4242", s.runId.value)
        assertEquals(1, s.rulesVersion)
        assertEquals(61, s.day)
        assertEquals(Phase.PLANNING, s.phase)
        assertFalse(s.isEnded)
        assertEquals(235, s.weapons.size)
        assertEquals(615, s.events.size)
        assertTrue(s.heroes.isNotEmpty() && s.aliveHeroes().isNotEmpty())
        assertEquals(60, s.processedEndDayIds.size, "one processed End Day per played day")
        assertTrue(Invariants.check(s, engine.config).isEmpty(), Invariants.check(s, engine.config).toString())
        // The fixture exercises long weapon histories: one weapon carries 15 combat entries (VICTORY/SIEGE).
        assertEquals(15, s.weapons.values.maxOf { w -> w.history.count { it.kind in combatKinds } })
    }

    @Test
    fun decodedStateIsStableUnderTodaysCodec() {
        val again = SaveCodec.decodeRun(SaveCodec.encodeRun(decoded))
        assertEquals(decoded, again)
        assertEquals(decoded.rng, again.rng, "RNG state survives untouched")
        assertEquals(decoded.events, again.events, "event history survives untouched")
    }

    @Test
    fun decodedStateAcceptsAnEndDay() {
        val outcome = engine.handle(decoded, Command.EndDay(endDayId(decoded)))
        val accepted = assertIs<CommandOutcome.Accepted>(outcome)
        val next = accepted.state
        assertTrue(Invariants.check(next, engine.config).isEmpty())
        assertEquals(61, accepted.resolution?.day)
        if (!next.isEnded) assertEquals(62, next.day)
        assertTrue(next.events.any { it.day == 61 }, "the End Day emitted events")
    }
}
