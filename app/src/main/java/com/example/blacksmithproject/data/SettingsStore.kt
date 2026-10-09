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

/** Non-authoritative player settings (GDD 13.3): never gameplay state. */
class SettingsStore(private val context: Context) {
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val seenTipsKey = stringSetPreferencesKey("seen_tips")
    private val dismissedReportKey = stringPreferencesKey("dismissed_report")

    val reducedMotion: Flow<Boolean> = context.settingsDataStore.data.map { it[reducedMotionKey] ?: false }

    suspend fun setReducedMotion(value: Boolean) {
        context.settingsDataStore.edit { it[reducedMotionKey] = value }
    }

    /** Onboarding tips the player has dismissed (IDs only; never gameplay state). */
    val seenTips: Flow<Set<String>> = context.settingsDataStore.data.map { it[seenTipsKey] ?: emptySet() }

    suspend fun markTipSeen(id: String) {
        context.settingsDataStore.edit { it[seenTipsKey] = (it[seenTipsKey] ?: emptySet()) + id }
    }

    /** Command ID of the last day report the player closed, so an unread one reopens after process death (GDD 3.3). */
    suspend fun dismissedReport(): String? = context.settingsDataStore.data.first()[dismissedReportKey]

    suspend fun setDismissedReport(commandId: String) {
        context.settingsDataStore.edit { it[dismissedReportKey] = commandId }
    }
}
