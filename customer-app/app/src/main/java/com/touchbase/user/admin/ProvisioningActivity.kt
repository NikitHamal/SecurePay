package com.touchbase.user.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.touchbase.user.util.SecureLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.touchbase.user.MainActivity
import com.touchbase.user.ui.provisioning.ProvisioningScreen
import com.touchbase.user.ui.theme.SecurePayTheme

class ProvisioningActivity : ComponentActivity() {

    private var provisioningManager: ProvisioningManager? = null
    private var policyController: DevicePolicyController? = null

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

        // Every step here is wrapped so the DPC process can NEVER die during the
        // provisioning-success handoff — that would roll back to "something went wrong".
        runCatching { enableEdgeToEdge() }
        provisioningManager = runCatching { ProvisioningManager(this) }.getOrNull()
        policyController = runCatching { DevicePolicyController(this) }.getOrNull()

        val action = runCatching { intent?.action }.getOrNull()
        if (action == DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL) {
            ProvisioningExtrasStore.recordStage(this, "PROVISIONING_SUCCESSFUL")
            ProvisioningExtrasStore.persistFromIntent(this, intent)
            onProvisioningComplete()
            return
        }

        showProvisioningUi()
    }

    private fun onProvisioningComplete() {
        SecureLog.i(TAG, "Device owner provisioning completed!")

        runCatching {
            val componentName = ComponentName(this, SecurePayDeviceAdminReceiver::class.java)
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

            val isOwner = runCatching { dpm.isDeviceOwnerApp(packageName) }.getOrDefault(false)
            if (isOwner) {
                SecureLog.i(TAG, "Confirmed: app is now Device Owner")
            } else {
                SecureLog.w(TAG, "Provisioning completed but app is NOT device owner (admin-only)")
            }

            runCatching { dpm.setProfileName(componentName, "SecurePay") }
                .onFailure { SecureLog.w(TAG, "setProfileName failed: ${it.message}") }
        }.onFailure { SecureLog.e(TAG, "onProvisioningComplete body failed", it) }

        // Launch MainActivity no matter what happened above. This MUST succeed or
        // Android's setup wizard reports a provisioning failure to the user.
        runCatching {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(mainIntent)
        }.onFailure {
            Log.e(TAG, "Failed to launch MainActivity after provisioning", it)
        }
        finish()
    }

    private fun showProvisioningUi() {
        val pm = provisioningManager
        val pc = policyController
        if (pm == null || pc == null) {
            // If managers failed to construct, jump straight to MainActivity so we
            // never block the user on a blank screen.
            runCatching {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            }
            finish()
            return
        }
        runCatching {
            setContent {
                SecurePayTheme {
                    ProvisioningScreen(
                        provisioningManager = pm,
                        policyController = pc,
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
        }.onFailure {
            Log.e(TAG, "Compose UI failed; launching MainActivity directly", it)
            runCatching {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            }
            finish()
        }
    }

    companion object {
        private const val TAG = "ProvisioningActivity"
    }
}
