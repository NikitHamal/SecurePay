package com.securepay.customer.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.securepay.customer.ui.theme.SecurePayTheme

class ProvisioningActivity : ComponentActivity() {

    private lateinit var provisioningManager: ProvisioningManager
    private lateinit var policyController: DevicePolicyController

    private val provisioningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.i(TAG, "Provisioning flow completed, rechecking state")
        recreate()
    }

    private val enableAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.i(TAG, "Enable admin flow completed, rechecking state")
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        provisioningManager = ProvisioningManager(this)
        policyController = DevicePolicyController(this)

        if (intent?.action == DevicePolicyManager.ACTION_PROVISIONING_COMPLETED) {
            onProvisioningComplete()
            return
        }

        showProvisioningUi()
    }

    private fun onProvisioningComplete() {
        Log.i(TAG, "Device owner provisioning completed!")
        val componentName = ComponentName(this, SecurePayDeviceAdminReceiver::class.java)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        runCatching {
            dpm.setProfileName(componentName, "SecurePay")
        }.onFailure { Log.w(TAG, "setProfileName failed: ${it.message}") }

        val mainIntent = Intent(this, com.securepay.customer.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(mainIntent)
        finish()
    }

    private fun showProvisioningUi() {
        setContent {
            SecurePayTheme {
                ProvisioningScreen(
                    provisioningManager = provisioningManager,
                    policyController = policyController,
                    onProvisioningComplete = {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        })
                        finish()
                    },
                    onLaunchProvisioning = { intent ->
                        provisioningLauncher.launch(intent)
                    },
                    onLaunchEnableAdmin = { intent ->
                        enableAdminLauncher.launch(intent)
                    }
                )
            }
        }
    }

    companion object {
        private const val TAG = "ProvisioningActivity"
    }
}