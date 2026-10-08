package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.persistence.SaveEnvelope
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** GDD 15.1: atomic save/restore and schema migration never mutate histories or RNG state. */
class MigrationTest {
    private val json = SaveCodec.json

    private fun postRunState(): GameState = SimulationDriver().playRun(LegacyProfile(), 4242, Policy.BALANCED_FAIR).second

    private fun envelope(schemaVersion: Int, payload: String) =
        """{"schemaVersion":$schemaVersion,"payload":${json.encodeToString(String.serializer(), payload)}}"""

    @Test
    fun rejectsNewerSchemaVersions() {
        val state = engine.newRun(LegacyProfile(), 1)
        val payload = json.encodeToString(GameState.serializer(), state)
        val newer = envelope(SaveCodec.SCHEMA_VERSION + 1, payload)
        assertFailsWith<IllegalArgumentException> { SaveCodec.decodeRun(newer) }
        assertFailsWith<IllegalArgumentException> { SaveCodec.decodeLegacy(envelope(SaveCodec.SCHEMA_VERSION + 1, json.encodeToString(LegacyProfile.serializer(), LegacyProfile()))) }
        assertFailsWith<IllegalArgumentException> { SaveCodec.decodeRun(envelope(0, payload)) }
    }

    @Test
    fun fullPostRunStateRoundTripsByteEqual() {
        val state = postRunState()
        assertTrue(state.isEnded && state.events.size > 100 && state.weapons.size > 20, "needs a rich state: ${state.events.size} events")
        val text = SaveCodec.encodeRun(state)
        val decoded = SaveCodec.decodeRun(text)
        assertEquals(state, decoded)
        assertEquals(text, SaveCodec.encodeRun(decoded), "encode(decode(x)) must be byte-equal")
        assertEquals(state.rng, decoded.rng)
        assertEquals(state.events, decoded.events)
        val legacyText = SaveCodec.encodeLegacy(state.legacy)
        assertEquals(legacyText, SaveCodec.encodeLegacy(SaveCodec.decodeLegacy(legacyText)))
    }

    /**
     * A v1 save written before optional fields existed: every key that has a default is stripped from the payload
     * (top level and nested). Decoding must fill the defaults; everything else (RNG, events, day, gold) is untouched.
     */
    @Test
    fun decodesStoredV1FixtureWithoutDefaultedKeys() {
        var state = engine.newRun(LegacyProfile(), 7)
        val forged = state.forgeAccepted(quickSword())
        state = forged.state.run(Command.ToggleShelf(forged.forgedWeaponId!!, true, 10))
        repeat(3) { state = state.endDay() }
        val full = json.parseToJsonElement(json.encodeToString(GameState.serializer(), state)).jsonObject
        val stripped = buildJsonObject {
            full.forEach { (k, v) ->
                when (k) {
                    "endCause" -> Unit // nullable with default; world/blessings have no default and must stay
                    "town" -> put(k, without(v.jsonObject, "siegesSurvived", "siegesLost"))
                    "heroes" -> put(k, JsonObject(v.jsonObject.mapValues { (_, h) -> without(h.jsonObject, "loyalty", "fame", "fate", "lastActivity", "descendantOf", "kills", "victories", "diedOnDay") }))
                    "weapons" -> put(k, JsonObject(v.jsonObject.mapValues { (_, w) -> without(w.jsonObject, "catalystId", "kills", "victories", "siegesDefended", "fame", "title", "history", "signatureId") }))
                    "legacy" -> put(k, JsonObject(emptyMap()))
                    else -> put(k, v)
                }
            }
        }
        // The fixture is the literal v1 envelope text as it would sit in the Room row.
        val fixture = envelope(1, json.encodeToString(JsonObject.serializer(), stripped))
        assertTrue(fixture.startsWith("""{"schemaVersion":1,"payload":"""))
        assertTrue(""""signatureId"""" !in fixture && """"siegesLost"""" !in fixture)
        val decoded = SaveCodec.decodeRun(fixture)
        val expected = state.copy(
            endCause = null, legacy = LegacyProfile(),
            town = state.town.copy(siegesSurvived = 0, siegesLost = 0),
            heroes = state.heroes.mapValues { (_, h) -> h.copy(loyalty = 0, fame = 0, fate = HeroFate.ALIVE, lastActivity = HeroActivity.IDLE, descendantOf = null, kills = 0, victories = 0, diedOnDay = null) },
            weapons = state.weapons.mapValues { (_, w) -> w.copy(catalystId = null, kills = 0, victories = 0, siegesDefended = 0, fame = 0, title = null, history = emptyList(), signatureId = null) },
        )
        assertEquals(expected, decoded)
        assertEquals(state.rng, decoded.rng, "RNG state survives migration untouched")
        assertEquals(state.events, decoded.events, "event history survives migration untouched")
        assertTrue(state.weapons.values.any { it.history.isNotEmpty() }, "fixture exercised a weapon with history")
    }

    @Test
    fun toleratesUnknownKeysSoAddedFieldsMigrateForward() {
        val state = postRunState()
        val payload = json.parseToJsonElement(json.encodeToString(GameState.serializer(), state)).jsonObject
        val firstHero = payload.getValue("heroes").jsonObject.keys.first()
        val withExtras = buildJsonObject {
            payload.forEach { (k, v) ->
                if (k == "heroes") put(k, JsonObject(v.jsonObject.mapValues { (id, h) -> if (id == firstHero) JsonObject(h.jsonObject + ("futureHeroField" to JsonPrimitive("x"))) else h }))
                else put(k, v)
            }
            put("futureTopLevelField", JsonPrimitive(123))
            put("futureNested", buildJsonObject { put("a", JsonPrimitive(true)) })
        }
        val payloadText = json.encodeToString(JsonObject.serializer(), withExtras)
        val envelopeWithExtra = """{"schemaVersion":1,"futureEnvelopeField":"y","payload":${json.encodeToString(String.serializer(), payloadText)}}"""
        assertEquals(state, SaveCodec.decodeRun(envelopeWithExtra))
    }

    @Test
    fun migrateIsANoOpForTheCurrentSchema() {
        val payload = json.encodeToString(LegacyProfile.serializer(), LegacyProfile(points = 9))
        val env = SaveEnvelope(SaveCodec.SCHEMA_VERSION, payload)
        val migrated = SaveCodec.migrate(env)
        assertEquals(env, migrated)
        assertTrue(migrated.payload === payload, "v1 -> v1 must not rewrite the payload")
        assertEquals(1, SaveCodec.SCHEMA_VERSION, "bumping the schema requires a registered migration step and a fixture test")
    }

    private fun without(obj: JsonObject, vararg keys: String): JsonObject = JsonObject(obj.filterKeys { it !in keys })

}
