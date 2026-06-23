package com.touchbase.user.admin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.touchbase.user.util.SecureLog

class PolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            ProvisioningExtrasStore.recordStage(this, "ADMIN_POLICY_COMPLIANCE")
        }.onFailure {
            SecureLog.w(TAG, "recordStage failed: ${it.message}")
        }

        runCatching {
            ProvisioningExtrasStore.persistFromIntent(this, intent)
        }.onFailure {
            SecureLog.w(TAG, "persistFromIntent failed: ${it.message}")
        }

        runCatching {
            ProvisioningExtrasStore.recordStage(this, "ADMIN_POLICY_COMPLIANT")
        }.onFailure {
            SecureLog.w(TAG, "recordStage compliant failed: ${it.message}")
        }

        setResult(RESULT_OK, Intent())
        finish()
    }

    companion object {
        private const val TAG = "PolicyCompliance"
    }
}
