package com.touchbase.user.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Android 12+ mandatory DPC provisioning entry point.
 *
 * Samsung A03 Core (SM-A032F, API 33) and other budget Samsung devices are
 * extremely fragile during QR provisioning. This activity does NOTHING except
 * return FULLY_MANAGED_DEVICE. No logging, no storage, no threads, no
 * SharedPreferences — any of these can crash the Samsung provisioning sandbox.
 *
 * Crash-safety: the entire body is wrapped in runCatching. A crash here is
 * rendered as "Something went wrong" with no recourse.
 */
class GetProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            // Samsung A03 Core: do NOT call ProvisioningExtrasStore or SecureLog here.
            // The device-protected storage context may not be initialized yet in
            // the provisioning sandbox, and SecureLog creates a background thread
            // which can abort the callback before the system reads the result.

            // Some Samsung devices require the component name to be echoed back
            // in the result intent, even though the QR already specifies it.
            val admin = ComponentName(this, SecurePayDeviceAdminReceiver::class.java)

            val result = Intent().apply {
                putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )
                putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                    admin
                )
            }
            setResult(RESULT_OK, result)
            Log.i(TAG, "GetProvisioningModeActivity: returned FULLY_MANAGED_DEVICE")
        }.onFailure { throwable ->
            Log.e(TAG, "GetProvisioningModeActivity crash — returning fallback", throwable)
            // Last-resort: try to return the mode even if the component name extra failed
            runCatching {
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
        }

        finish()
    }

    companion object {
        private const val TAG = "GetProvisioningMode"
    }
}
