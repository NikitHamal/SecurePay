package com.securepay.customer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.securepay.customer.admin.DevicePolicyController
import com.securepay.customer.admin.ProvisioningManager
import com.securepay.customer.admin.SecurityChecker
import com.securepay.customer.ui.SecurePayApp
import com.securepay.customer.ui.theme.SecurePayTheme
import com.securepay.customer.worker.HeartbeatWorker

class MainActivity : ComponentActivity() {

    private lateinit var policyController: DevicePolicyController
    private lateinit var provisioningManager: ProvisioningManager

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

        runSecurityChecks()
        checkAndContinue()
    }

    private fun runSecurityChecks() {
        val report = SecurityChecker.runAllChecks(this)
        if (report.isRooted) {
            Log.w(TAG, "SECURITY: Rooted device detected!")
        }
        if (report.isEmulator) {
            Log.w(TAG, "SECURITY: Emulator environment detected")
        }
        if (report.isDebuggable) {
            Log.i(TAG, "SECURITY: Running debug build")
        }
    }

    private fun checkAndContinue() {
        if (!policyController.isAdminActive) {
            Log.i(TAG, "Device admin not active, requesting activation")
            enableAdminLauncher.launch(policyController.enableAdminIntent())
            return
        }

        HeartbeatWorker.schedule(this)

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

    companion object {
        private const val TAG = "MainActivity"
    }
}