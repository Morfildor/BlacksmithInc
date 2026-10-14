package com.example.blacksmithproject.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** How the shop day moves on. TAP (the default) never advances by itself; 1x and 2x are opt-in (plan 6.5). */
/** How the shop day moves on. TAP is the default: nothing advances by itself (GDD: no time-pressure actions). */
enum class ShopDaySpeed(val label: String, val divisor: Int) {
    TAP("Manual", 0), X1("Auto 1x", 1), X2("Auto 2x", 2);

    val next: ShopDaySpeed get() = entries[(ordinal + 1) % entries.size]
}

/**
 * Where the guided first day stands for this installation. UNSET: never decided (a new installation, or one from before
 * the guidance existed). QUEUED: it starts with the next new game. ACTIVE: it runs in the run [IntroRecord.runId].
 * OFFERED: it was interrupted, or the player may never have seen it, and a small invitation asks. COMPLETED and SKIPPED
 * are final until the player asks for a replay.
 */
enum class IntroStatus { UNSET, QUEUED, ACTIVE, OFFERED, COMPLETED, SKIPPED }

/**
 * The guided first day, kept beside the settings and never in the save: a new game, an abandoned run or a new era does
 * not touch it. [reached] is the furthest step shown so far (a step never comes back); [startSerial] is the run's
 * weapon serial when the guidance began, so only weapons forged since then count. [completedOnce] survives a replay.
 */
data class IntroRecord(val status: IntroStatus = IntroStatus.UNSET, val runId: String? = null, val reached: Int = 0, val startSerial: Int = 0, val completedOnce: Boolean = false)

/** What the ViewModel and the screens need from settings; the seam that lets the ViewModel run in a JVM test. */
interface Settings {
    val reducedMotion: Flow<Boolean>
    val haptics: Flow<Boolean>
    val seenTips: Flow<Set<String>>
    val shopDaySpeed: Flow<ShopDaySpeed>
    suspend fun setReducedMotion(value: Boolean)
    suspend fun setHaptics(value: Boolean)
    suspend fun markTipSeen(id: String)
    suspend fun setShopDaySpeed(value: ShopDaySpeed)
    suspend fun dismissedReport(): String?
    suspend fun intro(): IntroRecord
    suspend fun setIntro(value: IntroRecord)
    /** The charter the player last picked for a new game: null when they never picked, [NO_CHARTER] for the shop alone. */
    suspend fun charterChoice(): String?
    suspend fun setCharterChoice(value: String)

    companion object { const val NO_CHARTER = "" }
}

/** Non-authoritative player settings (GDD 13.3): never gameplay state. */
class SettingsStore(private val context: Context) : Settings {
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val hapticsKey = booleanPreferencesKey("haptics")
    private val seenTipsKey = stringSetPreferencesKey("seen_tips")
    private val dismissedReportKey = stringPreferencesKey("dismissed_report")

    override val reducedMotion: Flow<Boolean> = context.settingsDataStore.data.map { it[reducedMotionKey] ?: false }

    override suspend fun setReducedMotion(value: Boolean) {
        context.settingsDataStore.edit { it[reducedMotionKey] = value }
    }

    /** On by default; off silences every vibration. */
    override val haptics: Flow<Boolean> = context.settingsDataStore.data.map { it[hapticsKey] ?: true }

    override suspend fun setHaptics(value: Boolean) {
        context.settingsDataStore.edit { it[hapticsKey] = value }
    }

    /** Onboarding tips the player has dismissed (IDs only; never gameplay state). */
    override val seenTips: Flow<Set<String>> = context.settingsDataStore.data.map { it[seenTipsKey] ?: emptySet() }

    override suspend fun markTipSeen(id: String) {
        context.settingsDataStore.edit { it[seenTipsKey] = (it[seenTipsKey] ?: emptySet()) + id }
    }

    private val shopDaySpeedKey = stringPreferencesKey("shop_day_speed")

    override val shopDaySpeed: Flow<ShopDaySpeed> = context.settingsDataStore.data.map { prefs -> ShopDaySpeed.entries.firstOrNull { it.name == prefs[shopDaySpeedKey] } ?: ShopDaySpeed.TAP }

    override suspend fun setShopDaySpeed(value: ShopDaySpeed) {
        context.settingsDataStore.edit { it[shopDaySpeedKey] = value.name }
    }

    /**
     * Command ID of the last day report the player closed in 0.6.0 or earlier. Read once after the update to place the
     * day cursor, which now lives beside the save; the key is no longer written.
     */
    override suspend fun dismissedReport(): String? = context.settingsDataStore.data.first()[dismissedReportKey]

    private val introStatusKey = stringPreferencesKey("intro_status")
    private val introRunKey = stringPreferencesKey("intro_run")
    private val introReachedKey = intPreferencesKey("intro_reached")
    private val introSerialKey = intPreferencesKey("intro_serial")
    private val introCompletedKey = booleanPreferencesKey("intro_completed_once")
    private val charterKey = stringPreferencesKey("charter_choice")

    /** A status this version does not know (written by a later one) reads as SKIPPED: the guidance never restarts by mistake. */
    override suspend fun intro(): IntroRecord = context.settingsDataStore.data.first().let { prefs ->
        val status = prefs[introStatusKey]?.let { name -> IntroStatus.entries.firstOrNull { it.name == name } ?: IntroStatus.SKIPPED } ?: IntroStatus.UNSET
        IntroRecord(status, prefs[introRunKey], prefs[introReachedKey] ?: 0, prefs[introSerialKey] ?: 0, prefs[introCompletedKey] ?: false)
    }

    override suspend fun setIntro(value: IntroRecord) {
        context.settingsDataStore.edit {
            it[introStatusKey] = value.status.name
            if (value.runId == null) it.remove(introRunKey) else it[introRunKey] = value.runId
            it[introReachedKey] = value.reached
            it[introSerialKey] = value.startSerial
            it[introCompletedKey] = value.completedOnce
        }
    }

    override suspend fun charterChoice(): String? = context.settingsDataStore.data.first()[charterKey]

    override suspend fun setCharterChoice(value: String) {
        context.settingsDataStore.edit { it[charterKey] = value }
    }
}
