package com.securepay.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.securepay.customer.admin.DevicePolicyController
import com.securepay.customer.ui.SecurePayApp
import com.securepay.customer.ui.theme.SecurePayTheme
import com.securepay.customer.worker.HeartbeatWorker

class MainActivity : ComponentActivity() {

    private lateinit var policyController: DevicePolicyController

    private val enableAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result intentionally ignored; controller re-checks live state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        policyController = DevicePolicyController(this)
        if (!policyController.isAdminActive) {
            enableAdminLauncher.launch(policyController.enableAdminIntent())
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
}