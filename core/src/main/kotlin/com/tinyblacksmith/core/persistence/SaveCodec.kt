package com.tinyblacksmith.core.persistence

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.market.QualityBand
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
    const val SCHEMA_VERSION = 3

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
    internal val runMigrations: Map<Int, (String) -> String> = mapOf(
        1 to { lowerOffBandCommissions(stampBalanceVersion(it)) },
        // Schema 3 adds the visit record, two event types (SHOP_DAY, MATERIAL_BOUGHT) and the visit enums. Nothing stored is
        // converted: the number rises so that an older build refuses a save it would otherwise read as corrupt.
        2 to { it },
    )
    internal val legacyMigrations: Map<Int, (String) -> String> = mapOf(1 to { it }, 2 to { it })

    /** Every schema-1 run was written by a build that had balance 5 and did not yet record it (0 = untracked). */
    private const val BALANCE_BEFORE_TRACKING = 5

    private fun stampBalanceVersion(payload: String): String {
        val run = json.parseToJsonElement(payload).jsonObject
        if ((run["balanceVersion"] as? JsonPrimitive)?.intOrNull?.takeIf { it != 0 } != null) return payload
        return JsonObject(run + ("balanceVersion" to JsonPrimitive(BALANCE_BEFORE_TRACKING))).toString()
    }

    /**
     * Schema-1 builds drew a commission's quality anywhere in 35..60 (60..75 for a noble one) and showed only the band
     * word; a commission now always asks for a band floor. An open one (offered or accepted) is lowered to the floor of
     * the band it was shown as, in the player's favour; its reward and every closed commission stay as they were.
     */
    private fun lowerOffBandCommissions(payload: String): String {
        val run = json.parseToJsonElement(payload).jsonObject
        val commissions = run["commissions"] as? JsonObject ?: return payload
        val bands = BalanceConfig.DEFAULT
        val lowered = commissions.mapValues { (_, c) ->
            val o = c.jsonObject
            val quality = (o["minQuality"] as? JsonPrimitive)?.intOrNull
            val floor = quality?.let { QualityBand.of(it, bands).floor(bands) }
            if (quality == null || floor == quality || (o["status"] as? JsonPrimitive)?.content !in setOf("OFFERED", "ACCEPTED")) c
            else JsonObject(o + ("minQuality" to JsonPrimitive(floor)))
        }
        return if (lowered == commissions) payload else JsonObject(run + ("commissions" to JsonObject(lowered))).toString()
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
