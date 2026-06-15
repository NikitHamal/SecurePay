package com.touchbase.agent.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "securepay_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _dealerId = MutableStateFlow<String?>(null)
    val dealerId: StateFlow<String?> = _dealerId.asStateFlow()

    private val _dealerName = MutableStateFlow<String?>(null)
    val dealerName: StateFlow<String?> = _dealerName.asStateFlow()

    init {
        _token.value = prefs.getString(KEY_TOKEN, null)
        _dealerId.value = prefs.getString(KEY_DEALER_ID, null)
        _dealerName.value = prefs.getString(KEY_DEALER_NAME, null)
    }

    fun saveSession(token: String, dealerId: String, dealerName: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_DEALER_ID, dealerId)
            .putString(KEY_DEALER_NAME, dealerName)
            .apply()
        _token.value = token
        _dealerId.value = dealerId
        _dealerName.value = dealerName
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _token.value = null
        _dealerId.value = null
        _dealerName.value = null
    }

    val isLoggedIn: Boolean get() = !_token.value.isNullOrEmpty()

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_DEALER_ID = "dealer_id"
        private const val KEY_DEALER_NAME = "dealer_name"
    }
}
