package com.touchbase.user.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class DeviceTokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "securepay_device_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var _accountId: String? = null
    val accountId: String? get() = _accountId

    private var _imei: String? = null
    val imei: String? get() = _imei

    init {
        _accountId = prefs.getString(KEY_ACCOUNT_ID, null)
        _imei = prefs.getString(KEY_IMEI, null)
    }

    fun saveDevice(accountId: String, imei: String) {
        prefs.edit()
            .putString(KEY_ACCOUNT_ID, accountId)
            .putString(KEY_IMEI, imei)
            .apply()
        _accountId = accountId
        _imei = imei
    }

    fun saveCachedStatus(nextPaymentDue: Long, lockedByDealer: Boolean) {
        prefs.edit()
            .putLong(KEY_CACHED_NEXT_DUE, nextPaymentDue)
            .putBoolean(KEY_CACHED_LOCKED_BY_DEALER, lockedByDealer)
            .apply()
    }

    fun saveServerTimeOffset(offsetMillis: Long) {
        prefs.edit()
            .putLong(KEY_SERVER_TIME_OFFSET, offsetMillis)
            .apply()
    }

    val cachedNextPaymentDue: Long get() = prefs.getLong(KEY_CACHED_NEXT_DUE, 0L)
    val cachedLockedByDealer: Boolean get() = prefs.getBoolean(KEY_CACHED_LOCKED_BY_DEALER, false)
    val serverTimeOffset: Long get() = prefs.getLong(KEY_SERVER_TIME_OFFSET, 0L)

    fun getTrustedTimeMillis(): Long {
        return System.currentTimeMillis() + serverTimeOffset
    }

    fun clear() {
        prefs.edit().clear().apply()
        _accountId = null
        _imei = null
    }

    val isRegistered: Boolean get() = !_accountId.isNullOrEmpty()

    companion object {
        private const val KEY_ACCOUNT_ID = "device_account_id"
        private const val KEY_IMEI = "device_imei"
        private const val KEY_CACHED_NEXT_DUE = "cached_next_payment_due"
        private const val KEY_CACHED_LOCKED_BY_DEALER = "cached_locked_by_dealer"
        private const val KEY_SERVER_TIME_OFFSET = "server_time_offset_millis"
    }
}
