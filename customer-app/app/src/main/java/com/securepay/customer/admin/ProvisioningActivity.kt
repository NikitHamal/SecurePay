package com.securepay.customer.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.securepay.customer.util.SecureLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.securepay.customer.MainActivity
import com.securepay.customer.ui.provisioning.ProvisioningScreen
import com.securepay.customer.ui.theme.SecurePayTheme

class ProvisioningActivity : ComponentActivity() {

    private lateinit var provisioningManager: ProvisioningManager
    private lateinit var policyController: DevicePolicyController

    private val provisioningLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        SecureLog.i(TAG, "Provisioning flow completed, rechecking state")
        recreate()
    }

    private val enableAdminLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        SecureLog.i(TAG, "Enable admin flow completed, rechecking state")
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        provisioningManager = ProvisioningManager(this)
        policyController = DevicePolicyController(this)

        if (intent?.action == DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL) {
            onProvisioningComplete()
            return
        }

        showProvisioningUi()
    }

    private fun onProvisioningComplete() {
        SecureLog.i(TAG, "Device owner provisioning completed!")
        val componentName = ComponentName(this, SecurePayDeviceAdminReceiver::class.java)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        runCatching {
            dpm.setProfileName(componentName, "SecurePay")
        }.onFailure { SecureLog.w(TAG, "setProfileName failed: ${it.message}") }

        val mainIntent = Intent(this, MainActivity::class.java).apply {
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
                    appContext = this@ProvisioningActivity,
                    onProvisioningComplete = {
                        startActivity(Intent(this@ProvisioningActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        })
                        finish()
                    },
                    onLaunchProvisioning = { intent: Intent ->
                        provisioningLauncher.launch(intent)
                    },
                    onLaunchEnableAdmin = { intent: Intent ->
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