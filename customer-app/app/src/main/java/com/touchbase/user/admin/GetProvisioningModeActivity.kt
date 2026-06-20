package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.touchbase.user.util.SecureLog

/**
 * Required by Android 12+ managed-device provisioning.
 *
 * The Setup Wizard asks the DPC which of the allowed management modes it supports.
 * SecurePay supports only fully managed, organization-owned devices.
 */
class GetProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            ProvisioningExtrasStore.recordStage(this, "GET_PROVISIONING_MODE")
            ProvisioningExtrasStore.persistFromIntent(this, intent)

            val fullyManaged = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            val allowedModes = intent.getIntegerArrayListExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
            )

            if (!allowedModes.isNullOrEmpty() && fullyManaged !in allowedModes) {
                SecureLog.e(TAG, "Fully managed mode was not offered by Setup Wizard")
                setResult(RESULT_CANCELED)
                finish()
                return
            }

            val result = Intent().apply {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, fullyManaged)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true)
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
                }
                // Preserve the QR-delivered one-time values for the compliance step.
                ProvisioningExtrasStore.adminExtras(intent)?.let { adminExtras ->
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, adminExtras)
                }
            }
            setResult(RESULT_OK, result)
        }.onFailure {
            SecureLog.e(TAG, "Failed to select provisioning mode", it)
            setResult(RESULT_CANCELED)
        }

        finish()
    }

    companion object {
        private const val TAG = "GetProvisioningMode"
    }
}
