package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import com.touchbase.user.util.SecureLog

/**
 * Android 12+ mandatory DPC provisioning entry point.
 *
 * This activity must respond quickly and reliably. Any crash, delay, or
 * mismatch in requested mode (e.g., returning MANAGED_PROFILE when the
 * system expects FULLY_MANAGED_DEVICE) causes the Samsung/Android setup
 * wizard to fail with "Something went wrong" or "Can't set up device".
 *
 * On Samsung A-series (Android 14-16) we have observed the app being
 * installed and this activity being invoked, but with zero diagnostic
 * signal coming back to the dealer dashboard. This rewrite adds best-effort
 * stage tracking and admin-extras persistence so we can see exactly how far
 * the Samsung Setup Wizard is getting before failing.
 *
 * Crash-safety contract: every operation is wrapped in runCatching{} so that
 * no exception ever propagates back to the system. A crash here is rendered
 * to the user as "Something went wrong" with no recourse.
 */
class GetProvisioningModeActivity : Activity() {

    private val tag = "GetProvisioningMode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Never let an exception escape to the system. The whole body is wrapped
        // so that even a corrupt intent or a SharedPreferences write failure
        // cannot surface as a "Something went wrong" provisioning failure.
        runCatching {
            // 1. Record entry stage immediately so we have proof the system reached us.
            ProvisioningExtrasStore.recordStage(this, "GET_PROVISIONING_MODE_INVOKED")

            // 2. Persist admin extras (best-effort, never throws). These are the
            //    values encoded in the QR (provisioningToken, activationCode,
            //    expectedImei, accountId, deviceId, dealerId, ...).
            runCatching { ProvisioningExtrasStore.persistFromIntent(this, intent) }
                .onFailure { SecureLog.w(tag, "Failed to persist admin extras: ${it.message}") }

            // 3. Read & log the system-supplied allowedModes array. This is purely
            //    diagnostic — we still unconditionally return FULLY_MANAGED_DEVICE
            //    below because falling back to MANAGED_PROFILE would re-introduce
            //    the admin-only bug that breaks our loan-security policies.
            val allowedModes = intent
                ?.getIntArrayExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
            SecureLog.i(tag, "allowedModes=${allowedModes?.toList()?.toString() ?: "null"}")

            // 4. For SecurePay, we ALWAYS want a Fully Managed Device (Device Owner).
            //    We deliberately ignore the 'allowedModes' list because returning
            //    anything else (like MANAGED_PROFILE) means the device will not be
            //    Device Owner, and our loan security policies cannot be enforced.
            val selectedMode = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE

            // 5. Record the post-decision stage. We log _BUT_NOT_ALLOWED if the
            //    mode we are about to return was not in the system-supplied set,
            //    so that on future field failures we can correlate "system expected
            //    X but we returned Y" with abort logs.
            val wasAllowed = allowedModes == null || allowedModes.contains(selectedMode)
            val stage = if (wasAllowed) {
                "GET_PROVISIONING_MODE_RETURNED_FULLY_MANAGED"
            } else {
                "GET_PROVISIONING_MODE_RETURNED_FULLY_MANAGED_BUT_NOT_ALLOWED"
            }
            ProvisioningExtrasStore.recordStage(this, stage)
            SecureLog.i(tag, "Returning mode=$selectedMode wasAllowed=$wasAllowed stage=$stage")

            setResult(
                RESULT_OK,
                Intent().apply {
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, selectedMode)
                }
            )
        }.onFailure { throwable ->
            // Last-resort safety net. Even if everything above blew up, we still
            // try to return FULLY_MANAGED_DEVICE so provisioning does not get
            // aborted by an unhandled crash in this entry point.
            SecureLog.provisioningError(
                tag,
                "Unexpected failure in GetProvisioningModeActivity.onCreate — " +
                    "attempting fallback result",
                throwable
            )
            runCatching {
                ProvisioningExtrasStore.recordStage(
                    this,
                    "GET_PROVISIONING_MODE_CRASH_FALLBACK"
                )
            }
            setResult(
                RESULT_OK,
                Intent().apply {
                    putExtra(
                        DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                        DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                    )
                }
            )
        }

        finish()
    }
}
