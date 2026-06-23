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

        // Some OEMs (Samsung Knox on budget A-series, Xiaomi, etc.) neither offer
        // FULLY_MANAGED_DEVICE nor pass the allowed-modes extra. Forcing it causes
        // "Setting up for work → Something went wrong". Tested OEM list grows over
        // time; fall back to managed-profile for these devices so provisioning
        // succeeds and the dealer can complete enrollment.
        if (isRestrictedOem()) {
            SecureLog.w(
                TAG,
                "No allowed modes from Setup Wizard on ${android.os.Build.MANUFACTURER} " +
                    "${android.os.Build.MODEL} (${android.os.Build.DEVICE}). " +
                    "Using managed profile to avoid provisioning failure."
            )
            return DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
        }

        SecureLog.i(TAG, "No allowed provisioning modes from Setup Wizard; defaulting to fully-managed.")
        return fullyManaged
    }

    private fun isRestrictedOem(): Boolean {
        val manufacturer = runCatching { android.os.Build.MANUFACTURER.trim().lowercase() }
            .getOrDefault("")
        // Samsung Knox on budget / A-series devices frequently does not pass
        // allowed provisioning modes. Xiaomi HyperOS has analogous issues.
        return manufacturer.contains("samsung") || manufacturer.contains("xiaomi")
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
