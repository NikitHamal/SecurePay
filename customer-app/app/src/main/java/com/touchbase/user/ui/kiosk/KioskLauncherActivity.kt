package com.touchbase.user.ui.kiosk

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.ui.provisioning.LockProScreen
import com.touchbase.user.ui.theme.SecurePayTheme
import com.touchbase.user.util.SecureLog

class KioskLauncherActivity : ComponentActivity() {

    private lateinit var policyController: DevicePolicyController

    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        SecureLog.i(TAG, "HOME role result: resultCode=${result.resultCode}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }

        policyController = DevicePolicyController(this)

        val tokenManager = runCatching { DeviceTokenManager(this) }.getOrNull()
        if (tokenManager?.isRegistered == true) {
            runCatching { KioskManager.restoreLauncher(this) }
            finish()
            return
        }

        runCatching { KioskManager.enterKioskMode(this) }
        requestHomeRole()

        // Enter lock task to block recent button
        runCatching { policyController.startLockTask(this) }

        SecureLog.i(TAG, "Kiosk home launched — provisioned but not registered")

        setContent {
            SecurePayTheme {
                // Block back button on LockProScreen — nothing to go back to
                BackHandler { }

                val pc = runCatching { DevicePolicyController(this@KioskLauncherActivity) }.getOrNull()
                LockProScreen(
                    onGetStarted = {
                        // Keep lock task active — blocks recent button on Login too
                        val intent = Intent(this, com.touchbase.user.MainActivity::class.java).apply {
                            putExtra("skip_lock_pro", true)
                        }
                        startActivity(intent)
                    },
                    onOpenWifi = {
                        // Temporarily stop lock task so WiFi panel can open
                        runCatching { policyController.stopLockTask(this@KioskLauncherActivity) }
                        pc?.openInternetSettings(this@KioskLauncherActivity)
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
        // Re-enter lock task when returning from WiFi settings
        runCatching { policyController.startLockTask(this) }
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as? RoleManager ?: return
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                SecureLog.i(TAG, "Requesting HOME role via RoleManager")
                runCatching {
                    homeRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }.onFailure { SecureLog.e(TAG, "Failed to request HOME role", it) }
            }
        }
    }

    companion object {
        private const val TAG = "KioskLauncher"
    }
}
