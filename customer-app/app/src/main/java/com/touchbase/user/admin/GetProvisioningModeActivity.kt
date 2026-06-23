package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import com.touchbase.user.util.SecureLog

/**
 * Android 12+ admin-integrated provisioning entry point.
 *
 * Setup Wizard calls this after the DPC APK is downloaded, before ownership is
 * finalized. Keep it tiny, synchronous and local: returning an unsupported mode
 * or crashing here produces Samsung's generic "Something went wrong" screen.
 */
class GetProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fullyManaged = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
        val result = runCatching {
            val adminExtras = readAndPersistExtras()
            val allowedModes = allowedProvisioningModes(intent)

            if (allowedModes.isNotEmpty() && fullyManaged !in allowedModes) {
                SecureLog.w(TAG, "Setup Wizard did not offer fully-managed Device Owner mode: $allowedModes. Proceeding anyway.")
            }

            ProvisioningExtrasStore.recordStage(this, "GET_PROVISIONING_MODE_DEVICE_OWNER_SELECTED")
            val data = Intent().apply {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, fullyManaged)
            }
            RESULT_OK to data
        }.onFailure {
            SecureLog.e(TAG, "Critical failure during provisioning mode selection", it)
        }.getOrElse {
            // Last-resort fail-open to the only production-supported mode. This
            // path avoids a DPC process crash; Setup Wizard will still validate
            // whether fully-managed mode is allowed for the current enrollment.
            val data = Intent().apply {
                putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )
            }
            RESULT_OK to data
        }

        val (code, data) = result
        setResult(code, data)
        finish()
    }

    private fun readAndPersistExtras(): PersistableBundle? {
        val adminExtras = runCatching {
            ProvisioningExtrasStore.adminExtras(intent)
        }.onFailure {
            SecureLog.e(TAG, "Failed to get adminExtras", it)
        }.getOrNull()

        runCatching {
            ProvisioningExtrasStore.recordStage(this, "GET_PROVISIONING_MODE")
            if (adminExtras != null) {
                ProvisioningExtrasStore.persist(this, adminExtras)
            }
        }.onFailure {
            SecureLog.e(TAG, "Failed to persist provisioning stage or extras", it)
        }

        return adminExtras
    }

    private fun allowedProvisioningModes(source: Intent?): Set<Int> {
        source ?: return emptySet()

        val fromArrayList = runCatching {
            source.getIntegerArrayListExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
            )
        }.getOrNull()
        if (!fromArrayList.isNullOrEmpty()) return fromArrayList.toSet()

        // Some OEM builds have historically delivered framework extras with a
        // different concrete type. Be tolerant instead of crashing provisioning.
        return runCatching {
            val raw = source.extras?.get(DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
            when (raw) {
                is IntArray -> raw.toSet()
                is Array<*> -> raw.filterIsInstance<Int>().toSet()
                is Iterable<*> -> raw.filterIsInstance<Int>().toSet()
                else -> emptySet()
            }
        }.getOrDefault(emptySet())
    }

    companion object {
        private const val TAG = "GetProvisioningMode"
    }
}
