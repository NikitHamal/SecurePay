package com.touchbase.user.admin

import android.app.Activity
import android.os.Bundle
import com.touchbase.user.util.SecureLog

/**
 * Android 10+/12+ managed-provisioning compliance handoff.
 *
 * On modern Android this is the important finalization point: ManagedProvisioning
 * launches this after the DPC is installed and promoted to the requested owner
 * mode. Keep it small, synchronous and local. A crash or slow network call here
 * makes Setup Wizard show Samsung's generic "Something went wrong" screen.
 */
class PolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val finalizerResult = runCatching {
            ProvisioningFinalizer.finalizeProvisioning(
                context = this,
                sourceIntent = intent,
                stage = "ADMIN_POLICY_COMPLIANCE"
            )
        }.onFailure {
            SecureLog.provisioningError(TAG, "Provisioning finalizer failed during compliance", it)
            runCatching { ProvisioningExtrasStore.recordStage(this, "ADMIN_POLICY_COMPLIANCE_FINALIZER_FAILED") }
        }.getOrNull()

        runCatching {
            ProvisioningExtrasStore.recordStage(
                this,
                when {
                    finalizerResult?.isDeviceOwner == true -> "ADMIN_POLICY_COMPLIANT_DEVICE_OWNER"
                    finalizerResult?.isAdminActive == true -> "ADMIN_POLICY_COMPLIANT_ADMIN_ONLY"
                    else -> "ADMIN_POLICY_COMPLIANT_NO_ADMIN"
                }
            )
        }

        // Do not launch UI or perform network work while Setup Wizard is waiting
        // for this result. Returning RESULT_OK lets Android finish provisioning;
        // ACTION_PROVISIONING_SUCCESSFUL / launcher startup will bring up TB User.
        setResult(RESULT_OK, ProvisioningFinalizer.buildSetupWizardResult())
        finish()
    }

    companion object {
        private const val TAG = "PolicyCompliance"
    }
}
