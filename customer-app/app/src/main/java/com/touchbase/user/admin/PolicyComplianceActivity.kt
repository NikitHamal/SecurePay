package com.touchbase.user.admin

import android.app.Activity
import android.os.Bundle
import com.touchbase.user.util.SecureLog

/**
 * Required by Android 12+ managed-device provisioning.
 *
 * This is the real finalization callback during QR provisioning from Setup Wizard on
 * Android 12+. Keep it local and synchronous: no network, no Compose, no WorkManager.
 */
class PolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val successful = runCatching {
            ProvisioningFinalizer.finalizeProvisioning(
                context = this,
                sourceIntent = intent,
                stage = "ADMIN_POLICY_COMPLIANCE"
            )
            ProvisioningExtrasStore.recordStage(this, "ADMIN_POLICY_COMPLIANT")
            setResult(
                RESULT_OK,
                ProvisioningFinalizer.buildSetupWizardResult(this, intent)
            )
            true
        }.onFailure {
            SecureLog.e(TAG, "Compliance finalization failed", it)
        }.getOrDefault(false)

        if (!successful) {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    companion object {
        private const val TAG = "PolicyCompliance"
    }
}
