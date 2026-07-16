package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.touchbase.user.util.SecureLog
import com.touchbase.user.MainActivity

class ProvisioningActivity : Activity() {

    private var provisioningManager: ProvisioningManager? = null
    private var policyController: DevicePolicyController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        provisioningManager = runCatching { ProvisioningManager(this) }.getOrNull()
        policyController = runCatching { DevicePolicyController(this) }.getOrNull()

        val action = runCatching { intent?.action }.getOrNull()
        if (action == DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL) {
            ProvisioningExtrasStore.recordStage(this, "PROVISIONING_SUCCESSFUL")
            ProvisioningExtrasStore.persistFromIntent(this, intent)
            onProvisioningComplete()
            return
        }

        runCatching { startActivity(MainActivity.newLaunchIntent(this)) }
            .onFailure { Log.e(TAG, "Failed to launch MainActivity", it) }
        finish()
    }

    private fun onProvisioningComplete() {
        SecureLog.i(TAG, "Device owner provisioning completion callback reached")

        runCatching {
            ProvisioningFinalizer.finalizeProvisioning(
                context = this,
                sourceIntent = intent,
                stage = "PROVISIONING_SUCCESSFUL"
            )
        }.onFailure { SecureLog.provisioningError(TAG, "Provisioning finalizer failed", it) }

        runCatching {
            startActivity(MainActivity.newLaunchIntent(this))
        }.onFailure {
            Log.e(TAG, "Failed to launch MainActivity after provisioning", it)
        }
        finish()
    }

    companion object {
        private const val TAG = "ProvisioningActivity"
    }
}
