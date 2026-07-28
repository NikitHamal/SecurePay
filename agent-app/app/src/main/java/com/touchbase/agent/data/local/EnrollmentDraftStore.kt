package com.touchbase.agent.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.touchbase.agent.ui.enrollment.EnrollmentDraftSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.enrollmentDraftDataStore by preferencesDataStore(name = "enrollment_draft")

/**
 * Persists an in-progress enrollment so the agent can "Save & continue later"
 * and resume where they left off on the next visit.
 *
 * The draft contains only the customer's application data (no tokens or
 * secrets), so plain DataStore-preferences holding a JSON blob is the right
 * tool here — unlike [WifiSettingsStore], no encryption is required.
 */
class EnrollmentDraftStore(context: Context) {
    private val appContext = context.applicationContext

    private val json = Json {
        // Tolerate fields added/removed between app versions so an older saved
        // draft never crashes a newer build (or vice-versa).
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun save(snapshot: EnrollmentDraftSnapshot) {
        appContext.enrollmentDraftDataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(snapshot)
        }
    }

    suspend fun load(): EnrollmentDraftSnapshot? {
        val raw = appContext.enrollmentDraftDataStore.data.first()[KEY] ?: return null
        return runCatching { json.decodeFromString<EnrollmentDraftSnapshot>(raw) }.getOrNull()
    }

    suspend fun clear() {
        appContext.enrollmentDraftDataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("snapshot_json")
    }
}
