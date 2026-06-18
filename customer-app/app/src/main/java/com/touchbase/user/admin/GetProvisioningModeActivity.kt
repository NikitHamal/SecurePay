package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import com.touchbase.user.util.SecureLog

/**
 * Required by Android 12+ managed-device provisioning.
 *
 * The Setup Wizard asks the DPC which of the allowed management modes it supports.
 * This product only supports a fully managed, organization-owned device.
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
                return@runCatching
            }

            val result = Intent().apply {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, fullyManaged)
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
