package com.example.blacksmithproject.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** What the ViewModel and the screens need from settings; the seam that lets the ViewModel run in a JVM test. */
interface Settings {
    val reducedMotion: Flow<Boolean>
    val seenTips: Flow<Set<String>>
    suspend fun setReducedMotion(value: Boolean)
    suspend fun markTipSeen(id: String)
    suspend fun dismissedReport(): String?
}

/** Non-authoritative player settings (GDD 13.3): never gameplay state. */
class SettingsStore(private val context: Context) : Settings {
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val seenTipsKey = stringSetPreferencesKey("seen_tips")
    private val dismissedReportKey = stringPreferencesKey("dismissed_report")

    override val reducedMotion: Flow<Boolean> = context.settingsDataStore.data.map { it[reducedMotionKey] ?: false }

    override suspend fun setReducedMotion(value: Boolean) {
        context.settingsDataStore.edit { it[reducedMotionKey] = value }
    }

    /** Onboarding tips the player has dismissed (IDs only; never gameplay state). */
    override val seenTips: Flow<Set<String>> = context.settingsDataStore.data.map { it[seenTipsKey] ?: emptySet() }

    override suspend fun markTipSeen(id: String) {
        context.settingsDataStore.edit { it[seenTipsKey] = (it[seenTipsKey] ?: emptySet()) + id }
    }

    /**
     * Command ID of the last day report the player closed in 0.6.0 or earlier. Read once after the update to place the
     * day cursor, which now lives beside the save; the key is no longer written.
     */
    override suspend fun dismissedReport(): String? = context.settingsDataStore.data.first()[dismissedReportKey]
}
