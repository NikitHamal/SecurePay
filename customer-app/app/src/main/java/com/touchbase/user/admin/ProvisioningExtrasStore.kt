package com.touchbase.user.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import com.touchbase.user.util.SecureLog

/**
 * Stores the one-time values delivered by Android's managed-device provisioning flow.
 *
 * Device-protected storage is used because these activities can run before normal
 * credential-protected storage is fully available during Setup Wizard.
 */
object ProvisioningExtrasStore {
    const val EXTRA_SCHEMA_VERSION = "schemaVersion"
    const val EXTRA_PROVISIONING_TOKEN = "provisioningToken"
    const val EXTRA_ACTIVATION_CODE = "activationCode"
    const val EXTRA_EXPECTED_IMEI = "expectedImei"
    const val EXTRA_ACCOUNT_ID = "accountId"
    const val EXTRA_DEVICE_ID = "deviceId"
    const val EXTRA_DEALER_ID = "dealerId"
    const val EXTRA_FRP_ACCOUNT_IDS_CSV = "frpAccountIdsCsv"
    const val EXTRA_SECURITY_POLICY_VERSION = "securityPolicyVersion"

    private const val PREFS_NAME = "tb_provisioning_state"
    private const val KEY_LAST_STAGE = "lastStage"
    private const val KEY_LAST_STAGE_AT = "lastStageAt"
    private const val KEY_REPORTED = "provisioningReported"
    private const val TAG = "ProvisioningExtras"

    fun persistFromIntent(context: Context, intent: Intent?) {
        val extras = adminExtras(intent) ?: return
        persist(context, extras)
    }

    fun persist(context: Context, extras: PersistableBundle) {
        val editor = prefs(context).edit()
        for (key in extras.keySet()) {
            when (val value = extras[key]) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        editor.apply()
        SecureLog.i(TAG, "Provisioning admin extras saved")
    }

    fun recordStage(context: Context, stage: String) {
        prefs(context).edit()
            .putString(KEY_LAST_STAGE, stage)
            .putLong(KEY_LAST_STAGE_AT, System.currentTimeMillis())
            .apply()
        SecureLog.i(TAG, "Provisioning stage: $stage")
    }

    fun activationCode(context: Context): String? =
        prefs(context).getString(EXTRA_ACTIVATION_CODE, null)
            ?.takeIf { it.matches(Regex("\\d{6}")) }

    fun provisioningToken(context: Context): String? =
        prefs(context).getString(EXTRA_PROVISIONING_TOKEN, null)
            ?.takeIf { it.length >= 32 }

    fun expectedImei(context: Context): String? =
        prefs(context).getString(EXTRA_EXPECTED_IMEI, null)
            ?.takeIf { it.matches(Regex("\\d{15}")) }

    fun frpAccountIds(context: Context): List<String> =
        prefs(context).getString(EXTRA_FRP_ACCOUNT_IDS_CSV, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.matches(Regex("^[0-9]{6,32}$")) }
            ?.distinct()
            .orEmpty()

    fun securityPolicyVersion(context: Context): Long =
        prefs(context).getLong(EXTRA_SECURITY_POLICY_VERSION, 0L)

    fun isProvisioningReported(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REPORTED, false)

    fun markProvisioningReported(context: Context) {
        prefs(context).edit().putBoolean(KEY_REPORTED, true).apply()
    }

    fun clearActivationCode(context: Context) {
        prefs(context).edit().remove(EXTRA_ACTIVATION_CODE).apply()
    }

    fun clearOneTimeToken(context: Context) {
        prefs(context).edit()
            .remove(EXTRA_PROVISIONING_TOKEN)
            .remove(KEY_REPORTED)
            .apply()
    }

    @Suppress("DEPRECATION")
    fun adminExtras(intent: Intent?): PersistableBundle? {
        intent ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                PersistableBundle::class.java
            )
        } else {
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        }
    }

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
