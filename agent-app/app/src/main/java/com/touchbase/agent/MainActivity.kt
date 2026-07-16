package com.touchbase.agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.touchbase.agent.ui.components.CrashDebugScreen
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.data.remote.TokenManager
import com.touchbase.agent.ui.navigation.SecurePayNavHost
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SecurePayAgentApplication
        val tokenManager = app.tokenManager
        val repository = app.repository
        val crashPrefs = getSharedPreferences(CrashHandler.CRASH_PREFS, Context.MODE_PRIVATE)

        ThemeManager.init(applicationContext)

        setContent {
            SecurePayAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val crashTrace = crashPrefs.getString(CrashHandler.KEY_TRACE, null)
                    if (crashTrace != null) {
                        val deviceInfo = crashPrefs.getString(CrashHandler.KEY_DEVICE, "N/A") ?: "N/A"
                        CrashDebugScreen(
                            trace = crashTrace,
                            deviceInfo = deviceInfo,
                            onRestart = {
                                crashPrefs.edit()
                                    .remove(CrashHandler.KEY_TRACE)
                                    .remove(CrashHandler.KEY_THREAD)
                                    .remove(CrashHandler.KEY_TIME)
                                    .remove(CrashHandler.KEY_DEVICE)
                                    .remove(CrashHandler.KEY_API)
                                    .putBoolean(CrashHandler.KEY_SHOWING_CRASH, false)
                                    .apply()
                                val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                if (intent != null) {
                                    startActivity(intent)
                                    finish()
                                }
                            },
                            onDismiss = {
                                crashPrefs.edit()
                                    .remove(CrashHandler.KEY_TRACE)
                                    .remove(CrashHandler.KEY_THREAD)
                                    .remove(CrashHandler.KEY_TIME)
                                    .remove(CrashHandler.KEY_DEVICE)
                                    .remove(CrashHandler.KEY_API)
                                    .putBoolean(CrashHandler.KEY_SHOWING_CRASH, false)
                                    .apply()
                            }
                        )
                    } else {
                        val navController = androidx.navigation.compose.rememberNavController()
                        SecurePayNavHost(
                            navController = navController,
                            repository = repository,
                            tokenManager = tokenManager
                        )
                    }
                }
            }
        }
    }
}
