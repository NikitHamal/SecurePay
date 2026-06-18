package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import com.touchbase.user.util.SecureLog

/**
 * Required by Android 12+ managed-device provisioning.
 *
 * Keep this activity deliberately small and synchronous. Network calls, Compose,
 * WorkManager initialization, or any other failure-prone work here can make Setup
 * Wizard roll the whole device back and show "Something went wrong".
 */
class PolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = Intent()
        val successful = runCatching {
            ProvisioningExtrasStore.recordStage(this, "ADMIN_POLICY_COMPLIANCE")
            ProvisioningExtrasStore.persistFromIntent(this, intent)

            ProvisioningExtrasStore.adminExtras(intent)?.let { adminExtras ->
                result.putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                    adminExtras
                )
            }

            // The framework owns the provisioning transaction. Do not add a timing-sensitive
            // isDeviceOwnerApp() gate here: returning RESULT_CANCELED rolls the entire setup
            // back on some OEM builds even though ownership is still being finalized.
            true
        }.onFailure {
            SecureLog.e(TAG, "Compliance handoff failed", it)
        }.getOrDefault(false)

        if (successful) {
            ProvisioningExtrasStore.recordStage(this, "ADMIN_POLICY_COMPLIANT")
            setResult(RESULT_OK, result)
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    companion object {
        private const val TAG = "PolicyCompliance"
    }
}
