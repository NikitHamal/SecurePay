package com.securepay.customer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.securepay.customer.admin.DevicePolicyController
import com.securepay.customer.data.model.DeviceStatus
import com.securepay.customer.admin.ProvisioningManager
import com.securepay.customer.admin.SecurityChecker
import com.securepay.customer.data.remote.DeviceTokenManager
import com.securepay.customer.ui.SecurePayApp
import com.securepay.customer.ui.theme.SecurePayTheme
import com.securepay.customer.worker.HeartbeatWorker
import com.securepay.customer.worker.NetworkMonitor

class MainActivity : ComponentActivity() {

    private lateinit var policyController: DevicePolicyController
    private lateinit var provisioningManager: ProvisioningManager
    private lateinit var networkMonitor: NetworkMonitor

    private val enableAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndContinue()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        policyController = DevicePolicyController(this)
        provisioningManager = ProvisioningManager(this)
        networkMonitor = NetworkMonitor(this)

        runSecurityChecks()
        enforceCachedLockState()
        checkAndContinue()
    }

    private fun runSecurityChecks() {
        val report = SecurityChecker.runAllChecks(this)
        if (report.isRooted) {
            Log.w(TAG, "SECURITY: Rooted device detected — enforcing lock")
            policyController.enforceLock()
        }
        if (report.isTampered) {
            Log.w(TAG, "SECURITY: Tampered app detected — enforcing lock")
            policyController.enforceLock()
        }
        if (report.isEmulator) {
            Log.w(TAG, "SECURITY: Emulator environment detected")
        }
        if (report.isDebuggable) {
            Log.i(TAG, "SECURITY: Running debug build")
        }
    }

    private fun enforceCachedLockState() {
        val tokenManager = DeviceTokenManager(this)
        if (!tokenManager.isRegistered) return

        val cachedDue = tokenManager.cachedNextPaymentDue
        if (cachedDue <= 0L) return

        val trustedTime = tokenManager.getTrustedTimeMillis()
        val status = DeviceStatus.evaluate(cachedDue, tokenManager.cachedLockedByDealer, trustedTime)
        if (status == DeviceStatus.LOCKED) {
            Log.w(TAG, "Cached state indicates LOCKED — enforcing lock on startup")
            policyController.enforceLock()
        }
    }

    private fun checkAndContinue() {
        if (!policyController.isAdminActive) {
            Log.i(TAG, "Device admin not active, requesting activation")
            enableAdminLauncher.launch(policyController.enableAdminIntent())
            return
        }

        HeartbeatWorker.schedule(this)
        networkMonitor.startMonitoring()

        val repository = (application as SecurePayApplication).deviceRepository

        setContent {
            SecurePayTheme {
                SecurePayApp(
                    repository = repository,
                    policyController = policyController
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