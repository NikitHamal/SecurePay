package com.touchbase.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.data.remote.TokenManager
import com.touchbase.agent.ui.navigation.SecurePayNavHost
import com.touchbase.agent.ui.theme.SecurePayAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SecurePayAgentApplication
        val tokenManager = app.tokenManager
        val repository = app.repository

        setContent {
            SecurePayAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
