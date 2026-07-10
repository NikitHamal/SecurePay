package com.touchbase.user.data.remote

import android.content.Context
import com.touchbase.user.util.SecureLog

/** Recovers a missing device credential only through the original one-time activation proof. */
object DeviceAuthRecovery {
    private const val TAG = "DeviceAuthRecovery"

    suspend fun ensureDeviceApiSecret(
        context: Context,
        tokenManager: DeviceTokenManager = DeviceTokenManager(context)
    ): String? {
        tokenManager.apiSecret?.let { return it }
        val repaired = DeviceRegistrationRecovery.repair(context, tokenManager)
        val recovered = tokenManager.apiSecret
        if (!repaired || recovered.isNullOrBlank()) {
            SecureLog.w(TAG, "Per-device credential recovery failed; device must be reprovisioned if the one-time proof is unavailable")
            return null
        }
        return recovered
    }
}
