package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Android 10+/12+ managed-provisioning compliance handoff.
 *
 * Samsung A03 Core (SM-A032F, API 33) and other budget Samsung devices will
 * abort the entire provisioning flow if this activity does ANY work that
 * creates threads, accesses storage, or touches DevicePolicyManager.
 *
 * This activity does NOTHING except return RESULT_OK with compliance status.
 * All logging, diagnostics, and policy application are deferred to
 * ProvisioningActivity or MainActivity.onCreate().
 */
class PolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            val result = Intent().apply {
                putExtra(
                    "android.app.extra.PROVISIONING_COMPLIANCE_STATUS",
                    0 // COMPLIANCE_STATUS_COMPLIANT
                )
            }
            setResult(RESULT_OK, result)
            Log.i(TAG, "PolicyComplianceActivity: returned COMPLIANT")
        }.onFailure { throwable ->
            Log.e(TAG, "PolicyComplianceActivity crash — returning fallback", throwable)
            runCatching {
                setResult(RESULT_OK, Intent())
            }
        }

        finish()
    }

    companion object {
        private const val TAG = "PolicyCompliance"
    }
}
