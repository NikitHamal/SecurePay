package com.touchbase.user

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.touchbase.user.util.SecureLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.admin.ProvisioningManager
import com.touchbase.user.admin.ProvisioningExtrasStore
import com.touchbase.user.admin.SecurityChecker
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.ui.SecurePayApp
import com.touchbase.user.ui.activation.NotProvisionedScreen
import com.touchbase.user.ui.lock.LockTaskActivity
import com.touchbase.user.ui.theme.SecurePayTheme
import com.touchbase.user.worker.HeartbeatWorker
import com.touchbase.user.worker.AppUpdateWorker
import com.touchbase.user.worker.TrackingWorker
import com.touchbase.user.worker.NetworkMonitor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var policyController: DevicePolicyController? = null
    private var provisioningManager: ProvisioningManager? = null
    private var networkMonitor: NetworkMonitor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse intent for any direct testing/debug extras passed via ADB
        runCatching { ProvisioningExtrasStore.persistFromIntent(this, intent) }

        // Everything here is wrapped so the DPC process can NEVER crash during or
        // right after the provisioning handoff (which would roll back to
        // "something went wrong"). Construct each helper defensively.
        ProvisioningExtrasStore.recordStage(this, "MAIN_ACTIVITY_CREATED")
        runCatching { enableEdgeToEdge() }
        policyController = runCatching { DevicePolicyController(this) }.getOrNull()
        provisioningManager = runCatching { ProvisioningManager(this) }.getOrNull()
        networkMonitor = runCatching { NetworkMonitor(this) }.getOrNull()

        runCatching { runSecurityChecks() }
        if (runCatching { enforceCachedLockState() }.getOrDefault(false)) return
        runCatching { checkAndContinue() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runCatching { ProvisioningExtrasStore.persistFromIntent(this, intent) }
    }

    override fun onResume() {
        super.onResume()
        runCatching { enforceCachedLockState() }
    }

    private fun runSecurityChecks() {
        val pc = policyController ?: return
        val report = runCatching { SecurityChecker.runAllChecks(this) }.getOrElse {
            SecureLog.e(TAG, "SecurityChecker threw", it)
            return
        }
        if (report.isRooted) {
            SecureLog.w(TAG, "SECURITY: Rooted device detected — enforcing lock")
            runCatching { pc.enforceLock(DeviceTokenManager(this).cachedFrpAccountIds) }
        }
        if (report.isTampered) {
            SecureLog.w(TAG, "SECURITY: Tampered app detected — enforcing lock")
            runCatching { pc.enforceLock(DeviceTokenManager(this).cachedFrpAccountIds) }
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
        val pc = policyController ?: return false
        val tokenManager = runCatching { DeviceTokenManager(this) }.getOrElse {
            SecureLog.e(TAG, "DeviceTokenManager init failed in enforceCachedLockState", it)
            return false
        }
        if (!tokenManager.isRegistered) return false
        if (tokenManager.cachedReleaseApproved) return false

        runCatching { pc.applyBaseLoanSecurity(tokenManager.cachedFrpAccountIds) }

        val cachedDue = tokenManager.cachedNextPaymentDue
        if (cachedDue <= 0L) return false

        val trustedTime = runCatching { tokenManager.getTrustedTimeMillis() }.getOrDefault(System.currentTimeMillis())
        val status = runCatching {
            DeviceStatus.evaluate(cachedDue, tokenManager.cachedLockedByDealer, trustedTime)
        }.getOrNull() ?: return false

        return if (status == DeviceStatus.LOCKED) {
            SecureLog.w(TAG, "Cached state indicates LOCKED — enforcing lock + pinning on startup")
            runCatching { pc.enforceLock(tokenManager.cachedFrpAccountIds) }
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
        val pc = policyController
        val pm = provisioningManager
        val isAdminActive = pc?.let { runCatching { it.isAdminActive }.getOrDefault(false) } ?: false
        val isDeviceOwner = pm?.let { runCatching { it.isDeviceOwner }.getOrDefault(false) } ?: false
        val tokenManager = runCatching { DeviceTokenManager(this) }.getOrNull()
        val allowPostRelease = tokenManager?.cachedReleaseApproved == true

        // Production financing must run as Device Owner. Android's plain Device
        // Admin role cannot enforce DISALLOW_FACTORY_RESET, FRP, uninstall/app
        // restrictions, lock-task allowlists, or base loan security. Blocking here
        // prevents a manually-enabled admin app from activating a loan and giving
        // testers a false "provisioned" result.
        if (!isDeviceOwner && !allowPostRelease) {
            val state = if (isAdminActive) "admin-only" else "no-admin"
            SecureLog.w(TAG, "Blocking SecurePay app startup: not Device Owner (state=$state)")
            runCatching {
                setContent {
                    SecurePayTheme {
                        NotProvisionedScreen()
                    }
                }
            }
            return
        }

        runCatching { HeartbeatWorker.schedule(this) }
        runCatching { AppUpdateWorker.schedule(this) }
        runCatching { TrackingWorker.schedule(this) }
        networkMonitor?.let { runCatching { it.startMonitoring() } }

        val repository = runCatching {
            (application as SecurePayApplication).deviceRepository
        }.getOrNull()

        if (pc == null || repository == null) {
            Log.e(TAG, "Cannot render dashboard: policyController or repository unavailable")
            runCatching {
                setContent {
                    SecurePayTheme {
                        NotProvisionedScreen()
                    }
                }
            }
            return
        }

        reportProvisioningMilestone(repository)

        runCatching {
            setContent {
                SecurePayTheme {
                    SecurePayApp(
                        repository = repository,
                        policyController = pc,
                        onLocked = { launchLockTask() }
                    )
                }
            }
        }.onFailure {
            Log.e(TAG, "Failed to render SecurePayApp", it)
        }
    }


    private fun reportProvisioningMilestone(repository: com.touchbase.user.data.repository.DeviceRepository) {
        val token = ProvisioningExtrasStore.provisioningToken(this) ?: return
        if (ProvisioningExtrasStore.isProvisioningReported(this)) return
        val expectedImei = ProvisioningExtrasStore.expectedImei(this)

        lifecycleScope.launch {
            repository.reportProvisioned(token, expectedImei)
                .onSuccess {
                    ProvisioningExtrasStore.markProvisioningReported(this@MainActivity)
                    ProvisioningExtrasStore.recordStage(this@MainActivity, "PROVISIONING_REPORTED")
                }
                .onFailure {
                    SecureLog.w(TAG, "Provisioning milestone report will be retried: ${it.message}")
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor?.let { runCatching { it.stopMonitoring() } }
    }

    companion object {
        private const val TAG = "MainActivity"

        fun newLaunchIntent(context: android.content.Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }
}
