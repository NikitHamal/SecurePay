package com.securepay.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.securepay.agent.ui.AgentApp
import com.securepay.agent.ui.theme.SecurePayAgentTheme
import com.securepay.agent.viewmodel.AgentEnrollmentViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AgentEnrollmentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecurePayAgentTheme {
                AgentApp(viewModel)
            }
        }
    }
}
