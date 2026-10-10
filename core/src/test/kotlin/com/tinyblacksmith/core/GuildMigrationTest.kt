package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.admitted
import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.engine.Compatibility
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.persistence.SaveEnvelope
import com.tinyblacksmith.core.rng.RngState
import com.tinyblacksmith.core.rng.RngStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Schema 5 to 6 (plan C07, spec 16.2). The fixtures are saves written by 0.7.0 (rules 4, content 4, schema 5): the debug
 * scenario saves as that build wrote them. A run from before the guild loads whole, stays a classic run and plays on.
 */
class GuildMigrationTest {
    private val engine = TestSupport.engine
    private val fixtures = listOf("visitor_merchant", "visitor_unanswered", "chain_debt_repaid", "visitor_heirloom", "relic_full_workshop", "relic_bellows", "siege_long_assault")

    private fun text(name: String) = javaClass.classLoader.getResource("saves/v5_$name.json")!!.readText()
    /** The payload as schema 5 stored it, read without any migration: what must still be there afterwards. */
    private fun raw(name: String): GameState = SaveCodec.json.decodeFromString(SaveEnvelope.serializer(), text(name)).let {
        assertEquals(5, it.schemaVersion)
        SaveCodec.json.decodeFromString(GameState.serializer(), it.payload)
    }

    @Test fun `a schema 5 save loads whole and nothing in it is rewritten but the new stream`() {
        for (name in fixtures) {
            val before = raw(name)
            val after = SaveCodec.decodeRun(text(name))
            assertEquals(4, after.rulesVersion, name)
            assertNull(after.guild, "$name: a run from before the guild never grows one")
            assertEquals(RngState.seeded(after.seed, GameEngine.STREAM_SEED_VERSION).stateOf(RngStream.GUILD), after.rng.stateOf(RngStream.GUILD), name)
            assertEquals(before.rng.streams, after.rng.streams - RngStream.GUILD, "$name: every older stream is where it was")
            assertEquals(before.copy(rng = after.rng), after, "$name: weapons, heroes, visitor, relics, charges, orders, siege and reports are untouched")
        }
    }

    @Test fun `what each fixture was made to hold is still there`() {
        assertTrue(SaveCodec.decodeRun(text("visitor_merchant")).encounter!!.let { it.isOpen && it.blade != null }, "the merchant's offer, unanswered, with its stored blade")
        assertTrue(SaveCodec.decodeRun(text("visitor_unanswered")).encounter!!.isOpen)
        SaveCodec.decodeRun(text("relic_full_workshop")).let { s -> assertEquals(3, s.relics.size); assertTrue(s.pendingRelicOffer.isNotEmpty()) }
        assertNotNull(SaveCodec.decodeRun(text("siege_long_assault")).siege?.traitId)
        assertTrue(SaveCodec.decodeRun(text("chain_debt_repaid")).let { it.consequences.isNotEmpty() || it.encounter != null })
        // An heirloom order binds a blade: the promise survives.
        SaveCodec.decodeRun(text("visitor_heirloom")).let { s -> assertTrue(s.encounter != null || s.weapons.values.any { it.promisedTo != null }) }
    }

    @Test fun `it is admitted under the new rules and plays on as a classic run`() {
        for (name in fixtures) {
            val loaded = SaveCodec.decodeRun(text(name))
            assertTrue(Compatibility.admit(loaded, engine.content, engine.config) is Compatibility.Result.Admitted, name)
            var s = loaded.admitted()
            assertEquals(GameEngine.RULES_VERSION, s.rulesVersion)
            repeat(8) {
                if (!s.isEnded) s = s.endDay()
                assertNull(s.guild)
                assertTrue(s.weapons.values.none { it.isLoaned })
                assertNull(s.lastResolution?.mission)
                assertNull(s.lastResolution?.siege)
                assertEquals(emptyList(), Invariants.check(s, engine.config, engine.shelfSlots(s), engine.content), name)
                // A relic of the guild is never offered to it.
                assertTrue(s.pendingRelicOffer.none { engine.content.relic(it)!!.effect == com.tinyblacksmith.core.content.RelicEffect.COMBAT })
            }
            // And it is written back under schema 6 and read again unchanged.
            assertEquals(s, SaveCodec.decodeRun(SaveCodec.encodeRun(s)))
        }
    }

    @Test fun `an older fixture still passes through every step to schema 6`() {
        val text = javaClass.classLoader.getResource("saves/v3_forced_seed4242_day61.json")!!.readText()
        val s = SaveCodec.decodeRun(text)
        assertNull(s.guild)
        assertTrue(RngStream.entries.all { it in s.rng.streams })
    }
}
