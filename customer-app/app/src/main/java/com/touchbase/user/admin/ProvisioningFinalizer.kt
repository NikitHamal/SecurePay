package com.touchbase.user.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.touchbase.user.MainActivity
import com.touchbase.user.util.SecureLog

/**
 * Shared, crash-resistant finalization for every Android managed-provisioning callback.
 *
 * Android 12+ setup-wizard provisioning finalizes through ACTION_ADMIN_POLICY_COMPLIANCE;
 * ACTION_PROVISIONING_SUCCESSFUL is not reliable in that path. Keep all work here
 * synchronous, local, and non-Compose so Setup Wizard never sees the DPC process die.
 */
object ProvisioningFinalizer {

    data class Result(
        val isDeviceOwner: Boolean,
        val isAdminActive: Boolean
    )

    fun finalizeProvisioning(context: Context, sourceIntent: Intent?, stage: String): Result {
        val appContext = context.applicationContext
        ProvisioningExtrasStore.recordStage(appContext, stage)
        ProvisioningExtrasStore.persistFromIntent(appContext, sourceIntent)

        val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(appContext, SecurePayDeviceAdminReceiver::class.java)

        val owner = waitForDeviceOwner(dpm, appContext.packageName)
        val adminActive = runCatching { dpm.isAdminActive(admin) }.getOrDefault(false)

        if (owner) {
            ProvisioningExtrasStore.recordStage(appContext, "DEVICE_OWNER_CONFIRMED")
            runCatching {
                DevicePolicyController(appContext).applyBaseLoanSecurity(
                    ProvisioningExtrasStore.frpAccountIds(appContext)
                )
            }.onFailure {
                SecureLog.w(TAG, "Initial Device Owner policy application failed: ${it.message}")
            }
            runCatching { dpm.setProfileName(admin, "SecurePay") }
                .onFailure { SecureLog.w(TAG, "setProfileName failed: ${it.message}") }
        } else {
            ProvisioningExtrasStore.recordStage(
                appContext,
                if (adminActive) "ADMIN_ONLY_AFTER_PROVISIONING" else "NO_ADMIN_AFTER_PROVISIONING"
            )
            SecureLog.w(
                TAG,
                "Provisioning callback reached without Device Owner. adminActive=$adminActive. " +
                    "Loan activation will stay blocked until the phone is re-provisioned."
            )
        }

        return Result(isDeviceOwner = owner, isAdminActive = adminActive)
    }

    fun buildSetupWizardResult(context: Context, sourceIntent: Intent?): Intent {
        return Intent().apply {
            ProvisioningExtrasStore.adminExtras(sourceIntent)?.let { adminExtras ->
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, adminExtras)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT, true)
                putExtra(DevicePolicyManager.EXTRA_RESULT_LAUNCH_INTENT, launchIntent(context))
            }
        }
    }

    fun launchIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private fun waitForDeviceOwner(dpm: DevicePolicyManager, packageName: String): Boolean {
        repeat(8) { attempt ->
            val owner = runCatching { dpm.isDeviceOwnerApp(packageName) }.getOrDefault(false)
            if (owner) return true
            if (attempt < 7) {
                runCatching { Thread.sleep(250L) }
            }
        }
        return false
    }

    private const val TAG = "ProvisioningFinalizer"
}
