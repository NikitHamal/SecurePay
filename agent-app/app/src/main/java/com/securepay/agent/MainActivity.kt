package com.securepay.agent

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.securepay.agent.admin.SecurityChecker
import com.securepay.agent.data.remote.SecurePayRepository
import com.securepay.agent.data.remote.TokenManager
import com.securepay.agent.ui.navigation.SecurePayNavHost
import com.securepay.agent.ui.theme.SecurePayAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SecurePayAgentApplication
        val tokenManager = app.tokenManager
        val repository = app.repository
        val report = app.securityReport

        if (report != null && report.shouldBlock) {
            val reasons = mutableListOf<String>()
            if (report.isRooted) reasons.add("Rooted device detected")
            if (report.isTampered) reasons.add("App tampering detected")
            Toast.makeText(this, "Security warning: ${reasons.joinToString(", ")}", Toast.LENGTH_LONG).show()
        }

        setContent {
            SecurePayAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
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