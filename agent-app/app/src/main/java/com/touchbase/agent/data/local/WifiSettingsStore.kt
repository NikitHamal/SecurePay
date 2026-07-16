package com.touchbase.agent.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class SavedWifiSettings(
    val ssid: String,
    val password: String
)

class WifiSettingsStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): SavedWifiSettings? {
        val ssid = prefs.getString(KEY_SSID, null)?.trim().orEmpty()
        if (ssid.isBlank()) return null
        return SavedWifiSettings(
            ssid = ssid,
            password = prefs.getString(KEY_PASSWORD, null).orEmpty()
        )
    }

    fun save(ssid: String, password: String) {
        val cleanSsid = ssid.trim()
        if (cleanSsid.isBlank()) {
            clear()
            return
        }
        prefs.edit()
            .putString(KEY_SSID, cleanSsid)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_SSID)
            .remove(KEY_PASSWORD)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "securepay_agent_wifi"
        const val KEY_SSID = "wifi_ssid"
        const val KEY_PASSWORD = "wifi_password"
    }
}
