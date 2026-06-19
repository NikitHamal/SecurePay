package com.touchbase.user.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class DeviceTokenManager private constructor(
    private val prefs: SharedPreferences,
    private val isEncrypted: Boolean
) {

    constructor(context: Context) : this(
        prefs = openPrefs(context),
        isEncrypted = prefsEncryptedOk
    )

    private var _accountId: String? = prefs.getString(KEY_ACCOUNT_ID, null)
    val accountId: String? get() = _accountId

    private var _imei: String? = prefs.getString(KEY_IMEI, null)
    val imei: String? get() = _imei

    fun saveDevice(accountId: String, imei: String) {
        prefs.edit()
            .putString(KEY_ACCOUNT_ID, accountId)
            .putString(KEY_IMEI, imei)
            .apply()
        _accountId = accountId
        _imei = imei
    }

    fun saveCachedStatus(nextPaymentDue: Long, lockedByDealer: Boolean, releaseApproved: Boolean = false) {
        prefs.edit()
            .putLong(KEY_CACHED_NEXT_DUE, nextPaymentDue)
            .putBoolean(KEY_CACHED_LOCKED_BY_DEALER, lockedByDealer)
            .putBoolean(KEY_CACHED_RELEASE_APPROVED, releaseApproved)
            .apply()
    }

    fun saveServerTimeOffset(offsetMillis: Long) {
        prefs.edit()
            .putLong(KEY_SERVER_TIME_OFFSET, offsetMillis)
            .apply()
    }

    val cachedNextPaymentDue: Long get() = prefs.getLong(KEY_CACHED_NEXT_DUE, 0L)
    val cachedLockedByDealer: Boolean get() = prefs.getBoolean(KEY_CACHED_LOCKED_BY_DEALER, false)
    val cachedReleaseApproved: Boolean get() = prefs.getBoolean(KEY_CACHED_RELEASE_APPROVED, false)
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
        private const val TAG = "DeviceTokenManager"
        private const val PREFS_NAME = "securepay_device_auth"
        private const val KEY_ACCOUNT_ID = "device_account_id"
        private const val KEY_IMEI = "device_imei"
        private const val KEY_CACHED_NEXT_DUE = "cached_next_payment_due"
        private const val KEY_CACHED_LOCKED_BY_DEALER = "cached_locked_by_dealer"
        private const val KEY_CACHED_RELEASE_APPROVED = "cached_release_approved"
        private const val KEY_SERVER_TIME_OFFSET = "server_time_offset_millis"

        // Set by openPrefs() during the (synchronous) primary constructor call.
        @Volatile private var prefsEncryptedOk: Boolean = false

        private fun openPrefs(context: Context): SharedPreferences {
            val appContext = context.applicationContext
            // Try encrypted prefs first. On a freshly-provisioned device the keystore
            // may not be ready (no lock screen yet), which can throw — fall back to
            // plain prefs so the DPC never crashes on first launch.
            return runCatching {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val p = EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                prefsEncryptedOk = true
                p
            }.getOrElse {
                Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs: ${it.message}")
                prefsEncryptedOk = false
                appContext.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
            }
        }

        /** Public fallback constructor used by SecurePayApplication if construction throws. */
        fun fallback(context: Context): DeviceTokenManager {
            return runCatching { DeviceTokenManager(context) }.getOrElse {
                Log.e(TAG, "DeviceTokenManager fallback to plain prefs", it)
                val appContext = context.applicationContext
                val plain = appContext.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
                DeviceTokenManager(plain, isEncrypted = false)
            }
        }
    }
}
