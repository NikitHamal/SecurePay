package com.touchbase.user.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.touchbase.user.MainActivity
import com.touchbase.user.util.SecureLog

/**
 * Shared, crash-resistant finalization for Android managed-provisioning callbacks.
 *
 * Android 12+ setup-wizard provisioning finalizes through
 * ACTION_ADMIN_POLICY_COMPLIANCE. ACTION_PROVISIONING_SUCCESSFUL remains as a
 * fallback for older/legacy flows. Keep this work synchronous and local so Setup
 * Wizard never sees the DPC process die during enrollment.
 */
object ProvisioningFinalizer {

    data class Result(
        val isDeviceOwner: Boolean,
        val isAdminActive: Boolean
    )

    fun finalizeProvisioning(context: Context, sourceIntent: Intent?, stage: String): Result {
        val appContext = context.applicationContext
        
        runCatching {
            ProvisioningExtrasStore.recordStage(appContext, stage)
        }.onFailure {
            SecureLog.w(TAG, "recordStage failed: ${it.message}")
        }

        runCatching {
            ProvisioningExtrasStore.persistFromIntent(appContext, sourceIntent)
        }.onFailure {
            SecureLog.w(TAG, "persistFromIntent failed: ${it.message}")
        }

        val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(appContext, SecurePayDeviceAdminReceiver::class.java)

        val owner = waitForDeviceOwner(dpm, appContext.packageName)
        val adminActive = runCatching { dpm.isAdminActive(admin) }.getOrDefault(false)

        if (owner) {
            runCatching {
                ProvisioningExtrasStore.recordStage(appContext, "DEVICE_OWNER_CONFIRMED")
            }.onFailure {
                SecureLog.w(TAG, "recordStage DEVICE_OWNER_CONFIRMED failed: ${it.message}")
            }
            runCatching {
                DevicePolicyController(appContext).applyBaseLoanSecurity(
                    ProvisioningExtrasStore.frpAccountIds(appContext)
                )
            }.onFailure {
                SecureLog.w(TAG, "Initial Device Owner policy application failed: ${it.message}")
            }
            runCatching { dpm.setProfileName(admin, "TB User") }
                .onFailure { SecureLog.w(TAG, "setProfileName failed: ${it.message}") }
        } else {
            runCatching {
                ProvisioningExtrasStore.recordStage(
                    appContext,
                    if (adminActive) "ADMIN_ONLY_AFTER_PROVISIONING" else "NO_ADMIN_AFTER_PROVISIONING"
                )
            }.onFailure {
                SecureLog.w(TAG, "recordStage stage failure failed: ${it.message}")
            }
            SecureLog.w(
                TAG,
                "Provisioning callback reached without Device Owner. adminActive=$adminActive. " +
                    "Loan activation will stay blocked until the phone is re-provisioned."
            )
        }

        return Result(isDeviceOwner = owner, isAdminActive = adminActive)
    }

    /**
     * Result sent back to Setup Wizard from ADMIN_POLICY_COMPLIANCE.
     *
     * Keep this result intentionally small. The QR/admin extras have already been
     * persisted, and some OEM setup wizards are strict about unexpected result
     * extras during this step.
     */
    fun buildSetupWizardResult(context: Context): Intent {
        return Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(DevicePolicyManager.EXTRA_RESULT_LAUNCH_INTENT, launchIntent(context))
            }
        }
    }

    fun launchIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private fun waitForDeviceOwner(dpm: DevicePolicyManager, packageName: String): Boolean {
        repeat(3) { attempt ->
            val owner = runCatching { dpm.isDeviceOwnerApp(packageName) }.getOrDefault(false)
            if (owner) return true
            if (attempt < 2) {
                runCatching { Thread.sleep(50L) }
            }
        }
        return false
    }

    private const val TAG = "ProvisioningFinalizer"
}
