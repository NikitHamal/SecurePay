package com.touchbase.user.data.remote

import android.content.Context
import com.touchbase.user.BuildConfig
import com.touchbase.user.util.SecureLog

/**
 * Repairs legacy installs that were activated before the app started storing the
 * per-account device HMAC secret locally.
 *
 * Symptom before this fix:
 *   TrackingWorker / HeartbeatWorker failed with `HTTP 401` because the app sent
 *   accountId+imei but signed with the global APK HMAC secret. The dashboard then
 *   correctly expected the per-device secret and rejected the request.
 *
 * Recovery path:
 *   1. Call /api/device/check using the global secret and IMEI only.
 *   2. Dashboard returns the account's device API secret.
 *   3. Save that secret locally and use it for heartbeat, tracking and location.
 */
object DeviceAuthRecovery {
    private const val TAG = "DeviceAuthRecovery"

    suspend fun ensureDeviceApiSecret(
        context: Context,
        tokenManager: DeviceTokenManager = DeviceTokenManager(context)
    ): String? {
        tokenManager.apiSecret?.let { return it }

        val imei = tokenManager.imei
        if (imei.isNullOrBlank()) {
            SecureLog.w(TAG, "Cannot recover device API secret: missing IMEI")
            return null
        }

        return runCatching {
            val recoveryApi = ApiModule.provideApi(BuildConfig.HMAC_SECRET, "")
            val response = recoveryApi.deviceCheck(imei, null)
            val account = response.account
            val recoveredSecret = response.apiSecret.trim()

            if (account == null || recoveredSecret.length < 32) {
                SecureLog.w(TAG, "Device API secret recovery did not return a usable account/secret")
                return@runCatching null
            }

            tokenManager.saveDevice(account.id, imei, recoveredSecret)
            tokenManager.saveSecurityPolicy(response.securityPolicy)
            tokenManager.saveServerTimeOffset(response.serverTime - System.currentTimeMillis())
            tokenManager.saveCachedStatus(
                nextPaymentDue = account.nextPaymentDue,
                lockedByDealer = account.status.equals("LOCKED", ignoreCase = true) || account.status.equals("STOLEN", ignoreCase = true),
                releaseApproved = account.releaseApproved,
                isStolen = account.isStolen
            )
            SecureLog.i(TAG, "Recovered per-device API secret for account ${account.id}")
            recoveredSecret
        }.getOrElse {
            SecureLog.w(TAG, "Device API secret recovery failed: ${it.message}")
            null
        }
    }
}
