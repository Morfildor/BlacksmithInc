package com.tinyblacksmith.core.persistence

import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Versioned JSON envelope. Schema bumps go through [SaveCodec.migrate] so saved histories and RNG state are never mutated silently. */
@Serializable
data class SaveEnvelope(val schemaVersion: Int, val payload: String)

object SaveCodec {
    const val SCHEMA_VERSION = 2

    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        allowStructuredMapKeys = true
    }

    fun encodeRun(state: GameState): String = json.encodeToString(SaveEnvelope.serializer(), SaveEnvelope(SCHEMA_VERSION, json.encodeToString(GameState.serializer(), state)))

    fun decodeRun(text: String): GameState {
        val env = json.decodeFromString(SaveEnvelope.serializer(), text)
        return json.decodeFromString(GameState.serializer(), migrate(env, runMigrations).payload)
    }

    fun encodeLegacy(legacy: LegacyProfile): String = json.encodeToString(SaveEnvelope.serializer(), SaveEnvelope(SCHEMA_VERSION, json.encodeToString(LegacyProfile.serializer(), legacy)))

    fun decodeLegacy(text: String): LegacyProfile {
        val env = json.decodeFromString(SaveEnvelope.serializer(), text)
        return json.decodeFromString(LegacyProfile.serializer(), migrate(env, legacyMigrations).payload)
    }

    /**
     * Stepwise payload migrations keyed by the schema version they upgrade FROM; each returns the payload for
     * version + 1. Fields added with defaults need no step (unknown keys are ignored, missing keys take defaults);
     * a real step is needed only for renames, type changes, removed fields or converted values. Histories and RNG
     * state are never rewritten.
     *
     * The run and the legacy profile are different documents under one schema number, so each has its own table and
     * a step written for one is never applied to the other. Raising [SCHEMA_VERSION] needs an entry in BOTH tables
     * for the version left behind; the entry is `{ it }` for a document that does not change.
     */
    internal val runMigrations: Map<Int, (String) -> String> = mapOf(1 to ::stampBalanceVersion)
    internal val legacyMigrations: Map<Int, (String) -> String> = mapOf(1 to { it })

    /** Every schema-1 run was written by a build that had balance 5 and did not yet record it (0 = untracked). */
    private const val BALANCE_BEFORE_TRACKING = 5

    private fun stampBalanceVersion(payload: String): String {
        val run = json.parseToJsonElement(payload).jsonObject
        if ((run["balanceVersion"] as? JsonPrimitive)?.intOrNull?.takeIf { it != 0 } != null) return payload
        return JsonObject(run + ("balanceVersion" to JsonPrimitive(BALANCE_BEFORE_TRACKING))).toString()
    }

    /** [target] is a parameter only so tests can run a table past today's schema. */
    internal fun migrate(env: SaveEnvelope, steps: Map<Int, (String) -> String>, target: Int = SCHEMA_VERSION): SaveEnvelope {
        require(env.schemaVersion in 1..target) { "Save schema ${env.schemaVersion} is newer than supported $target" }
        var current = env
        while (current.schemaVersion < target) {
            val step = steps[current.schemaVersion] ?: error("No migration from schema ${current.schemaVersion}")
            current = SaveEnvelope(current.schemaVersion + 1, step(current.payload))
        }
        return current
    }
}

/**
 * Authoritative save boundary. Implementations must persist run + legacy atomically (Room transaction on Android,
 * a single map swap in memory) so a process death never leaves a run without its legacy profile or vice versa.
 */
interface SaveRepository {
    fun loadRun(): GameState?
    fun loadLegacy(): LegacyProfile
    /** Persists both documents in one atomic step. Passing null for [run] clears the current run. */
    fun saveAtomically(run: GameState?, legacy: LegacyProfile)
}

class InMemorySaveRepository : SaveRepository {
    private var runText: String? = null
    private var legacyText: String? = null

    override fun loadRun(): GameState? = runText?.let { SaveCodec.decodeRun(it) }
    override fun loadLegacy(): LegacyProfile = legacyText?.let { SaveCodec.decodeLegacy(it) } ?: LegacyProfile()

    override fun saveAtomically(run: GameState?, legacy: LegacyProfile) {
        val r = run?.let { SaveCodec.encodeRun(it) }
        val l = SaveCodec.encodeLegacy(legacy)
        synchronized(this) { runText = r; legacyText = l }
    }
}
