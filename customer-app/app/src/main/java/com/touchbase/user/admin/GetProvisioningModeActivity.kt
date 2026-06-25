package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import com.touchbase.user.util.SecureLog

class GetProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = runCatching {
            readAndPersistExtras()
            val allowedModes = allowedProvisioningModes(intent)

            val selectedMode = resolveProvisioningMode(allowedModes)

            if (selectedMode != null) {
                ProvisioningExtrasStore.recordStage(
                    this,
                    if (selectedMode == PROVISIONING_MODE_FULLY_MANAGED_DEVICE)
                        "GET_PROVISIONING_MODE_FULLY_MANAGED"
                    else
                        "GET_PROVISIONING_MODE_FALLBACK_$selectedMode"
                )
                RESULT_OK to Intent().apply {
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, selectedMode)
                }
            } else {
                RESULT_CANCELED to Intent()
            }
        }.onFailure {
            SecureLog.e(TAG, "Critical failure during provisioning mode selection", it)
        }.getOrElse {
            RESULT_CANCELED to Intent()
        }

        setResult(result.first, result.second)
        finish()
    }

    private fun resolveProvisioningMode(allowedModes: Set<Int>): Int? {
        val fullyManaged = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE

        if (fullyManaged in allowedModes) return fullyManaged

        if (allowedModes.isNotEmpty()) {
            SecureLog.w(
                TAG,
                "Setup Wizard does not allow fully-managed mode (allowed=$allowedModes). " +
                    "Falling back to first available mode."
            )
            return allowedModes.first()
        }

        SecureLog.i(TAG, "No allowed provisioning modes from Setup Wizard; defaulting to fully-managed.")
        return fullyManaged
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
        private val PROVISIONING_MODE_FULLY_MANAGED_DEVICE =
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
    }
}
