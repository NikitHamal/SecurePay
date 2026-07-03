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

        // CRITICAL: Samsung Knox (Android 14–16 / One UI 6–7) strictly forbids ANY
        // DevicePolicyManager access inside ADMIN_POLICY_COMPLIANCE. A single DPM
        // read (isDeviceOwnerApp / isAdminActive) aborts the entire flow with
        // "Something went wrong". We therefore do the absolute minimum here:
        // record the stage, persist the admin extras, and return RESULT_OK.
        // All DPM checks and policy application are deferred to
        // PROVISIONING_SUCCESSFUL or MainActivity.onCreate().
        runCatching {
            ProvisioningExtrasStore.recordStage(this, "ADMIN_POLICY_COMPLIANCE")
            ProvisioningExtrasStore.persistFromIntent(this, intent)
        }.onFailure {
            SecureLog.provisioningError(TAG, "Stage/extras persistence failed during compliance", it)
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
