package com.touchbase.user.admin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.touchbase.user.util.SecureLog

/**
 * Android 12+ compliance/finalization handoff.
 *
 * This callback runs inside Setup Wizard after Android has set the DPC as owner.
 * Any uncaught exception or invalid result here makes provisioning roll back to
 * Samsung's generic IT-team failure page, so the handler is deliberately minimal
 * and never performs network, Compose, WorkManager or long asynchronous work.
 */
class PolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val setupResult = runCatching {
            ProvisioningExtrasStore.recordStage(this, "ADMIN_POLICY_COMPLIANCE")
            ProvisioningFinalizer.finalizeProvisioning(
                context = this,
                sourceIntent = intent,
                stage = "ADMIN_POLICY_COMPLIANCE"
            )
            ProvisioningExtrasStore.recordStage(this, "ADMIN_POLICY_COMPLIANT")
            ProvisioningFinalizer.buildSetupWizardResult(this)
        }.onFailure {
            SecureLog.e(TAG, "Compliance finalization failed, but will attempt to return OK to avoid setup rollback", it)
        }.getOrElse {
            // Fallback: Even if something failed during finalization, return OK with the launch intent
            // so Setup Wizard doesn't roll back the device owner status.
            runCatching {
                ProvisioningFinalizer.buildSetupWizardResult(this)
            }.getOrDefault(Intent())
        }

        setResult(RESULT_OK, setupResult)
        finish()
    }

    companion object {
        private const val TAG = "PolicyCompliance"
    }
}
