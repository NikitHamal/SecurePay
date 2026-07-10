package com.touchbase.user.data.remote

import android.content.Context
import com.touchbase.user.BuildConfig
import com.touchbase.user.admin.ProvisioningExtrasStore
import com.touchbase.user.util.SecureLog

/**
 * Repairs an install where Device Owner provisioning completed but the local
 * account credential write did not finish. Recovery replays the same unexpired
 * one-time activation token; it never obtains a device secret from an IMEI-only lookup.
 */
object DeviceRegistrationRecovery {
    private const val TAG = "RegistrationRecovery"

    suspend fun repair(
        context: Context,
        tokenManager: DeviceTokenManager = DeviceTokenManager(context)
    ): Boolean {
        if (tokenManager.isRegistered && !tokenManager.imei.isNullOrBlank() && tokenManager.apiSecret != null) {
            return true
        }

        val token = ProvisioningExtrasStore.provisioningToken(context)
        val code = ProvisioningExtrasStore.activationCode(context)
        val expectedImei = ProvisioningExtrasStore.expectedImei(context)
        if (token.isNullOrBlank() || code.isNullOrBlank() || expectedImei.isNullOrBlank()) {
            SecureLog.w(TAG, "Registration repair requires the original provisioning token and activation code")
            return false
        }
        if (BuildConfig.HMAC_SECRET.isBlank()) {
            SecureLog.e(TAG, "Registration repair unavailable: bootstrap HMAC secret is not configured")
            return false
        }

        return runCatching {
            val response = ApiModule.provideApi(BuildConfig.HMAC_SECRET, "").activate(
                mapOf("activationCode" to code, "provisioningToken" to token, "imei" to expectedImei)
            )
            val account = response.account
            val imei = response.imei.ifBlank { response.device?.imei.orEmpty() }
            val apiSecret = response.apiSecret.trim()
            if (!response.activated || account == null || !imei.matches(Regex("\\d{15}")) || apiSecret.length < 32) {
                SecureLog.w(TAG, "Activation recovery did not return a usable account credential")
                return@runCatching false
            }

            tokenManager.saveDevice(account.id, imei, apiSecret)
            tokenManager.saveSecurityPolicy(response.securityPolicy)
            tokenManager.saveServerTimeOffset(response.serverTime - System.currentTimeMillis())
            tokenManager.saveCachedStatus(
                nextPaymentDue = account.nextPaymentDue,
                lockedByDealer = account.status.equals("LOCKED", true) || account.status.equals("STOLEN", true),
                releaseApproved = account.releaseApproved,
                isStolen = account.isStolen
            )
            SecureLog.i(TAG, "Registration repaired for account ${account.id}")
            true
        }.getOrElse {
            SecureLog.w(TAG, "Registration repair failed: ${it.message}")
            false
        }
    }
}
