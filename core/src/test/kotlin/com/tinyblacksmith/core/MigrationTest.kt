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
        val migrated = SaveCodec.migrate(env, SaveCodec.legacyMigrations)
        assertEquals(env, migrated)
        assertTrue(migrated.payload === payload, "the current schema must not rewrite the payload")
        assertEquals(4, SaveCodec.SCHEMA_VERSION, "bumping the schema requires a registered migration step and a fixture test")
    }

    private fun v1Fixture(): String = javaClass.getResource("/saves/v1_forced_seed4242_day61.json")?.readText() ?: error("missing v1 fixture")

    /**
     * Schema 1 -> 2 on the real v1 fixture: the run step stamps the balance the 0.6.0 build ran (5) and touches nothing
     * else; a payload that already names a balance version keeps it; the legacy document is carried over byte for byte.
     */
    @Test
    fun theRunStepStampsTheBalanceVersionAndNothingElse() {
        val env = json.decodeFromString(SaveEnvelope.serializer(), v1Fixture())
        val migrated = SaveCodec.migrate(env, SaveCodec.runMigrations, target = 2)
        assertEquals(2, migrated.schemaVersion)
        val before = json.parseToJsonElement(env.payload).jsonObject
        val after = json.parseToJsonElement(migrated.payload).jsonObject
        assertTrue("balanceVersion" !in before || before.getValue("balanceVersion") == JsonPrimitive(0), "the fixture predates the stamp")
        assertEquals(JsonPrimitive(5), after.getValue("balanceVersion"))
        assertEquals(before.filterKeys { it != "balanceVersion" }, after.filterKeys { it != "balanceVersion" }, "no other key changes")

        val tracked = envelope(1, json.encodeToString(JsonObject.serializer(), JsonObject(before + ("balanceVersion" to JsonPrimitive(4)))))
        assertEquals(4, SaveCodec.decodeRun(tracked).balanceVersion, "a recorded balance version is not overwritten")

        val legacy = SaveEnvelope(1, json.encodeToString(LegacyProfile.serializer(), LegacyProfile(points = 9)))
        assertEquals(SaveEnvelope(2, legacy.payload), SaveCodec.migrate(legacy, SaveCodec.legacyMigrations, target = 2))
    }

    /** Schema 2 -> 3 converts nothing: the number rises only so that an older build refuses the new enum constants. Shown on the real v2 fixture. */
    @Test
    fun theSchemaThreeStepCarriesBothDocumentsOverUnchanged() {
        val v2 = javaClass.getResource("/saves/v2_forced_seed4242_day61.json")?.readText() ?: error("missing v2 fixture")
        val env = json.decodeFromString(SaveEnvelope.serializer(), v2)
        assertEquals(2, env.schemaVersion)
        assertEquals(SaveEnvelope(3, env.payload), SaveCodec.migrate(env, SaveCodec.runMigrations, target = 3))
        val legacy = SaveEnvelope(2, json.encodeToString(LegacyProfile.serializer(), LegacyProfile(points = 9)))
        assertEquals(SaveEnvelope(3, legacy.payload), SaveCodec.migrate(legacy, SaveCodec.legacyMigrations, target = 3))
        // A day stored before the visit record reads as one: no snapshots, record version 0, the reasons typed.
        val last = SaveCodec.decodeRun(v2).lastResolution!!
        assertEquals(0, last.recordVersion)
        assertTrue(last.shopWeapons.isEmpty() && last.visits.isNotEmpty() && last.visits.all { it.customer == null && it.considered.isEmpty() && it.kind == VisitKind.BROWSE })
    }

    /** Schema 3 -> 4 converts nothing either: a hero written before the counter remembered them reads as a newcomer nobody has kept waiting. Shown on the real v3 fixture. */
    @Test
    fun theSchemaFourStepCarriesBothDocumentsOverUnchanged() {
        val v3 = javaClass.getResource("/saves/v3_forced_seed4242_day61.json")?.readText() ?: error("missing v3 fixture")
        val env = json.decodeFromString(SaveEnvelope.serializer(), v3)
        assertEquals(3, env.schemaVersion)
        assertEquals(SaveEnvelope(4, env.payload), SaveCodec.migrate(env, SaveCodec.runMigrations, target = 4))
        val legacy = SaveEnvelope(3, json.encodeToString(LegacyProfile.serializer(), LegacyProfile(points = 9)))
        assertEquals(SaveEnvelope(4, legacy.payload), SaveCodec.migrate(legacy, SaveCodec.legacyMigrations, target = 4))
        val run = SaveCodec.decodeRun(v3)
        assertTrue(run.heroes.isNotEmpty() && run.heroes.values.all { it.shopVisits == 0 && it.shopPurchases == 0 && it.turnedAwayStreak == 0 && it.lastServedDay == null && it.lastPurchaseDay == null && it.arrivedOnDay == 1 })
        assertTrue(run.lastResolution!!.turnedAway.isEmpty())
    }

    /** `MarketVisit.reason` is a String today and an enum from M2 whose first nine constants keep these spellings; every v1 day must still read. */
    @Test
    fun v1VisitReasonsDecode() {
        val nine = setOf("EMPTY_SHELVES", "TOO_EXPENSIVE", "NOT_BETTER", "OVERPRICED", "NOT_SUITED", "UNDECIDED", "WORN_OUT", "GREAT_FIT", "GOOD_ENOUGH")
        val visits = SaveCodec.decodeRun(v1Fixture()).lastResolution!!.visits
        assertTrue(visits.size >= 3, "the fixture's last day has visits: ${visits.size}")
        assertTrue(visits.all { it.reason.name in nine }, visits.map { it.reason }.toString())
    }

    /**
     * The run and the legacy profile share one schema number but not one migration table. The routing is shown with
     * injected tables run to schema 2 through each (the production run step is checked above).
     */
    @Test
    fun aRunStepIsNotAppliedToTheLegacyDocument() {
        val stampRun: (String) -> String = { payload ->
            JsonObject(json.parseToJsonElement(payload).jsonObject + ("balanceVersion" to JsonPrimitive(5))).toString()
        }
        val runSteps = mapOf(1 to stampRun)
        val legacySteps = mapOf<Int, (String) -> String>(1 to { it })
        val runPayload = json.encodeToString(GameState.serializer(), engine.newRun(LegacyProfile(), 1))
        val legacyPayload = json.encodeToString(LegacyProfile.serializer(), LegacyProfile(points = 9))

        val run = SaveCodec.migrate(SaveEnvelope(1, runPayload), runSteps, target = 2)
        assertEquals(2, run.schemaVersion)
        assertEquals(5, json.decodeFromString(GameState.serializer(), run.payload).balanceVersion)

        val legacy = SaveCodec.migrate(SaveEnvelope(1, legacyPayload), legacySteps, target = 2)
        assertEquals(SaveEnvelope(2, legacyPayload), legacy, "the legacy document is carried to the new schema unchanged")
        assertTrue("balanceVersion" !in legacy.payload)

        // A table without the step refuses; it never borrows the other document's step.
        assertFailsWith<IllegalStateException> { SaveCodec.migrate(SaveEnvelope(1, legacyPayload), emptyMap(), target = 2) }
        assertTrue(SaveCodec.runMigrations.keys == (1 until SaveCodec.SCHEMA_VERSION).toSet(), "one run step per schema version left behind")
        assertTrue(SaveCodec.legacyMigrations.keys == (1 until SaveCodec.SCHEMA_VERSION).toSet(), "one legacy step per schema version left behind")
    }

    /**
     * The strings every save written so far stores for a weapon's location (they were the class names until they were
     * pinned with `@SerialName`). Renaming or moving the classes must not change them; a new subclass adds a line.
     */
    @Test
    fun weaponLocationDiscriminatorsArePinned() {
        val stored = listOf(
            WeaponLocation.Storage to """{"type":"com.tinyblacksmith.core.model.WeaponLocation.Storage"}""",
            WeaponLocation.Shelf(84) to """{"type":"com.tinyblacksmith.core.model.WeaponLocation.Shelf","price":84}""",
            WeaponLocation.Owned(HeroId("h4"), true) to """{"type":"com.tinyblacksmith.core.model.WeaponLocation.Owned","heroId":"h4","equipped":true}""",
            WeaponLocation.Lost(22, "seized") to """{"type":"com.tinyblacksmith.core.model.WeaponLocation.Lost","day":22,"reason":"seized"}""",
            WeaponLocation.Destroyed(9) to """{"type":"com.tinyblacksmith.core.model.WeaponLocation.Destroyed","day":9}""",
        )
        val subclasses = WeaponLocation::class.java.declaredClasses.filter { WeaponLocation::class.java.isAssignableFrom(it) }
        assertEquals(subclasses.map { it.simpleName }.sorted(), stored.map { it.first::class.java.simpleName }.sorted(), "every WeaponLocation subclass is pinned")
        for ((location, text) in stored) {
            assertEquals(text, json.encodeToString(WeaponLocation.serializer(), location))
            assertEquals(location, json.decodeFromString(WeaponLocation.serializer(), text))
        }
        // The same strings inside a whole save: one weapon per location, through the envelope and back.
        val forged = engine.newRun(LegacyProfile(), 7).forgeAccepted(quickSword())
        val blade = forged.state.weapon(forged.forgedWeaponId!!)
        val weapons = stored.mapIndexed { i, (location, _) -> WeaponId("pin$i").let { it to blade.copy(id = it, location = location) } }.toMap()
        val state = forged.state.copy(weapons = weapons)
        val payload = json.decodeFromString(SaveEnvelope.serializer(), SaveCodec.encodeRun(state)).payload
        for ((_, text) in stored) assertTrue(""""location":$text""" in payload, "missing $text")
        assertEquals(state, SaveCodec.decodeRun(SaveCodec.encodeRun(state)))
    }

    private fun without(obj: JsonObject, vararg keys: String): JsonObject = JsonObject(obj.filterKeys { it !in keys })

}
