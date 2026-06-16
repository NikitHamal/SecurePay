package com.touchbase.user

import android.content.Intent
import android.os.Bundle
import com.touchbase.user.util.SecureLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.admin.ProvisioningManager
import com.touchbase.user.admin.SecurityChecker
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.ui.SecurePayApp
import com.touchbase.user.ui.activation.NotProvisionedScreen
import com.touchbase.user.ui.lock.LockTaskActivity
import com.touchbase.user.ui.theme.SecurePayTheme
import com.touchbase.user.util.BatteryOptimizationHelper
import com.touchbase.user.worker.HeartbeatWorker
import com.touchbase.user.worker.NetworkMonitor

class MainActivity : ComponentActivity() {

    private lateinit var policyController: DevicePolicyController
    private lateinit var provisioningManager: ProvisioningManager
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        policyController = DevicePolicyController(this)
        provisioningManager = ProvisioningManager(this)
        networkMonitor = NetworkMonitor(this)

        runSecurityChecks()
        if (enforceCachedLockState()) return
        checkAndContinue()
    }

    override fun onResume() {
        super.onResume()
        enforceCachedLockState()
    }

    private fun runSecurityChecks() {
        val report = SecurityChecker.runAllChecks(this)
        if (report.isRooted) {
            SecureLog.w(TAG, "SECURITY: Rooted device detected — enforcing lock")
            policyController.enforceLock()
        }
        if (report.isTampered) {
            SecureLog.w(TAG, "SECURITY: Tampered app detected — enforcing lock")
            policyController.enforceLock()
        }
        if (report.isEmulator) {
            SecureLog.w(TAG, "SECURITY: Emulator environment detected")
        }
        if (report.isDebuggable) {
            SecureLog.i(TAG, "SECURITY: Running debug build")
        }
    }

    /**
     * @return true if a lock task activity was launched (caller should stop further setup).
     */
    private fun enforceCachedLockState(): Boolean {
        val tokenManager = DeviceTokenManager(this)
        if (!tokenManager.isRegistered) return false

        val cachedDue = tokenManager.cachedNextPaymentDue
        if (cachedDue <= 0L) return false

        val trustedTime = tokenManager.getTrustedTimeMillis()
        val status = DeviceStatus.evaluate(cachedDue, tokenManager.cachedLockedByDealer, trustedTime)
        return if (status == DeviceStatus.LOCKED) {
            SecureLog.w(TAG, "Cached state indicates LOCKED — enforcing lock + pinning on startup")
            policyController.enforceLock()
            launchLockTask()
            true
        } else {
            false
        }
    }

    private fun launchLockTask() {
        runCatching {
            val intent = Intent(this, LockTaskActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }.onFailure { SecureLog.e(TAG, "Failed to launch LockTaskActivity", it) }
    }

    private fun checkAndContinue() {
        val isProvisioned = policyController.isAdminActive || provisioningManager.isDeviceOwner

        if (!isProvisioned) {
            SecureLog.i(TAG, "Device not provisioned (no admin/owner) — showing not-provisioned UI")
            setContent {
                SecurePayTheme {
                    NotProvisionedScreen()
                }
            }
            return
        }

        HeartbeatWorker.schedule(this)
        networkMonitor.startMonitoring()
        BatteryOptimizationHelper.requestIfRegistered(this)

        val repository = (application as SecurePayApplication).deviceRepository

        setContent {
            SecurePayTheme {
                SecurePayApp(
                    repository = repository,
                    policyController = policyController,
                    onLocked = { launchLockTask() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.stopMonitoring()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
