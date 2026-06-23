package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.touchbase.user.util.SecureLog

/**
 * Android 12+ admin-integrated provisioning entry point.
 *
 * Setup Wizard calls this activity after the DPC APK is downloaded, before the
 * device owner is finalized. Keep this activity tiny, synchronous and local:
 * returning the wrong mode or crashing here produces Samsung's generic
 * "Something went wrong. Contact your IT team" screen.
 */
class GetProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = runCatching {
            val fullyManaged = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE

            // Try to extract adminExtras but don't fail if we can't
            val adminExtras = runCatching {
                ProvisioningExtrasStore.adminExtras(intent)
            }.onFailure {
                SecureLog.e(TAG, "Failed to get adminExtras", it)
            }.getOrNull()

            // Try to persist but don't fail if we can't
            runCatching {
                ProvisioningExtrasStore.recordStage(this, "GET_PROVISIONING_MODE")
                if (adminExtras != null) {
                    ProvisioningExtrasStore.persist(this, adminExtras)
                }
            }.onFailure {
                SecureLog.e(TAG, "Failed to persist provisioning stage or extras", it)
            }

            val allowedModes = allowedProvisioningModes(intent)
            if (allowedModes.isNotEmpty() && fullyManaged !in allowedModes) {
                SecureLog.w(TAG, "Fully managed mode was not offered by Setup Wizard: $allowedModes. Proceeding anyway.")
            }

            val data = Intent().apply {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, fullyManaged)

                // This extra is explicitly allowed as a result of
                // ACTION_GET_PROVISIONING_MODE for fully-managed provisioning.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true)
                }

                // Preserve QR one-time values for ADMIN_POLICY_COMPLIANCE. Android
                // merges this bundle with the original admin extras.
                if (adminExtras != null) {
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, adminExtras)
                }
            }
            RESULT_OK to data
        }.onFailure {
            SecureLog.e(TAG, "Critical failure during provisioning mode selection", it)
        }.getOrElse {
            // Even in the absolute worst-case crash scenario, return RESULT_OK and fully-managed
            // to allow the Setup Wizard to succeed in making us Device Owner.
            val data = Intent().apply {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true)
                }
            }
            RESULT_OK to data
        }

        val (code, data) = result
        if (data == null) setResult(code) else setResult(code, data)
        finish()
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
        // different concrete type. Be tolerant instead of cancelling provisioning.
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
