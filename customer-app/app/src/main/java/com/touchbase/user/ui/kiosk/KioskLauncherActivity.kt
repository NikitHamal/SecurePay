package com.touchbase.user.ui.kiosk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.ui.provisioning.LockProScreen
import com.touchbase.user.ui.theme.SecurePayTheme
import com.touchbase.user.util.SecureLog

class KioskLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }

        val tokenManager = runCatching { DeviceTokenManager(this) }.getOrNull()
        if (tokenManager?.isRegistered == true) {
            runCatching { KioskManager.restoreLauncher(this) }
            finish()
            return
        }

        runCatching { KioskManager.enterKioskMode(this) }
        val pc = runCatching { DevicePolicyController(this) }.getOrNull()
        runCatching { pc?.startLockTask(this) }

        SecureLog.i(TAG, "Kiosk home launched — provisioned but not registered")

        setContent {
            SecurePayTheme {
                LockProScreen(
                    onGetStarted = {
                        val intent = Intent(this, com.touchbase.user.MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val tokenManager = runCatching { DeviceTokenManager(this) }.getOrNull()
        if (tokenManager?.isRegistered == true) {
            runCatching { KioskManager.restoreLauncher(this) }
            finish()
            return
        }
        val pc = runCatching { DevicePolicyController(this) }.getOrNull()
        runCatching { pc?.startLockTask(this) }
    }

    companion object {
        private const val TAG = "KioskLauncher"
    }
}
