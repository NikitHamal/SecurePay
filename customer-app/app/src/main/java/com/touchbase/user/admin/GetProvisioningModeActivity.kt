package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

/**
 * Android 12+ mandatory DPC provisioning entry point.
 *
 * This activity must respond quickly and reliably. Any crash, delay, or
 * mismatch in requested mode (e.g., returning MANAGED_PROFILE when the
 * system expects FULLY_MANAGED_DEVICE) causes the Samsung/Android setup
 * wizard to fail with "Something went wrong" or "Can't set up device".
 */
class GetProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // For SecurePay, we ALWAYS want a Fully Managed Device (Device Owner).
        // We ignore the 'allowedModes' list because if we return anything else
        // (like MANAGED_PROFILE), the device will not be Device Owner, and
        // our loan security policies cannot be enforced.
        
        val selectedMode = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE

        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, selectedMode)
            }
        )
        finish()
    }
}
