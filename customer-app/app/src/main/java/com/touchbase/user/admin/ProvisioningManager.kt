package com.touchbase.user.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Build
import com.touchbase.user.util.SecureLog

enum class ProvisioningState {
    NOT_PROVISIONED,
    PROVISIONING_IN_PROGRESS,
    ADMIN_ACTIVE,
    DEVICE_OWNER
}

class ProvisioningManager(context: Context) {

    private val appContext = context.applicationContext
    private val dpm = appContext
        .getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(appContext, SecurePayDeviceAdminReceiver::class.java)

    val state: ProvisioningState
        get() {
            if (dpm.isDeviceOwnerApp(appContext.packageName)) return ProvisioningState.DEVICE_OWNER
            if (dpm.isAdminActive(admin)) return ProvisioningState.ADMIN_ACTIVE
            return ProvisioningState.NOT_PROVISIONED
        }

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(appContext.packageName)

    val isAdminActive: Boolean
        get() = dpm.isAdminActive(admin)

    fun createDeviceOwnerProvisioningIntent(): Intent? {
        if (isDeviceOwner) {
            SecureLog.i(TAG, "Already device owner")
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SecureLog.w(
                TAG,
                "Android 12+ must be provisioned from Setup Wizard using the dealer QR code"
            )
            return null
        }

        return try {
            @Suppress("DEPRECATION")
            Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE).apply {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
                putExtra("android.app.extra.PROVISIONING_DEVICE_ADMIN_LABEL", "SecurePay")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            SecureLog.e(TAG, "Device owner provisioning not supported: ${e.message}")
            null
        }
    }

    fun enableDeviceAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "SecurePay requires device management to enforce your financing terms."
            )
        }
    }

    fun createNfcProvisioningPayload(): NdefMessage? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SecureLog.w(TAG, "NFC provisioning is disabled on Android 12+; use the dealer QR flow")
            return null
        }

        return try {
            val records = listOf(
                NdefRecord.createMime(
                    MIME_TYPE,
                    buildProvisioningPayload().toByteArray(Charsets.UTF_8)
                )
            )
            NdefMessage(records.toTypedArray())
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to create NFC provisioning payload", e)
            null
        }
    }

    private fun buildProvisioningPayload(): String {
        return buildString {
            append("{")
            append("\"android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME\":\"${admin.flattenToString()}\"")
            append(",")
            append("\"android.app.extra.PROVISIONING_DEVICE_ADMIN_LABEL\":\"SecurePay\"")
            append("}")
        }
    }

    companion object {
        private const val TAG = "ProvisioningManager"
        private const val MIME_TYPE = "application/com.touchbase.user.provisioning"
    }
}
