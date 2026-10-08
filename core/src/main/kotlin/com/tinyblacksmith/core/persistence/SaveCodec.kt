package com.tinyblacksmith.core.persistence

import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versioned JSON envelope. Schema bumps go through [migrate] so saved histories and RNG state are never mutated silently. */
@Serializable
data class SaveEnvelope(val schemaVersion: Int, val payload: String)

object SaveCodec {
    const val SCHEMA_VERSION = 1

    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        allowStructuredMapKeys = true
    }

    fun encodeRun(state: GameState): String = json.encodeToString(SaveEnvelope.serializer(), SaveEnvelope(SCHEMA_VERSION, json.encodeToString(GameState.serializer(), state)))

    fun decodeRun(text: String): GameState {
        val env = json.decodeFromString(SaveEnvelope.serializer(), text)
        return json.decodeFromString(GameState.serializer(), migrate(env).payload)
    }

    fun encodeLegacy(legacy: LegacyProfile): String = json.encodeToString(SaveEnvelope.serializer(), SaveEnvelope(SCHEMA_VERSION, json.encodeToString(LegacyProfile.serializer(), legacy)))

    fun decodeLegacy(text: String): LegacyProfile {
        val env = json.decodeFromString(SaveEnvelope.serializer(), text)
        return json.decodeFromString(LegacyProfile.serializer(), migrate(env).payload)
    }

    /**
     * Stepwise payload migrations keyed by the schema version they upgrade FROM; each returns the payload for
     * version + 1. Fields added with defaults need no step (unknown keys are ignored, missing keys take defaults);
     * register a step only for renames, type changes or removed fields. Histories and RNG state are never rewritten.
     */
    private val migrations: Map<Int, (String) -> String> = emptyMap()

    internal fun migrate(env: SaveEnvelope): SaveEnvelope {
        require(env.schemaVersion in 1..SCHEMA_VERSION) { "Save schema ${env.schemaVersion} is newer than supported $SCHEMA_VERSION" }
        var current = env
        while (current.schemaVersion < SCHEMA_VERSION) {
            val step = migrations[current.schemaVersion] ?: error("No migration from schema ${current.schemaVersion}")
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
