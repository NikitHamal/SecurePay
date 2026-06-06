package com.securepay.customer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securepay.customer.domain.DeviceLeaseUiState
import com.securepay.customer.domain.DeviceStatus
import com.securepay.customer.policy.DevicePolicyController
import com.securepay.customer.ui.theme.AmberWarning
import com.securepay.customer.ui.theme.EmeraldActive
import com.securepay.customer.ui.theme.VividCrimson
import com.securepay.customer.viewmodel.CustomerViewModel

@Composable
fun CustomerApp(
    viewModel: CustomerViewModel,
    policyController: DevicePolicyController
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            ActiveDashboard(
                state = state,
                onSimulatePayment = viewModel::simulatePayment,
                onForceOverdue = viewModel::forceOverdueForTesting
            )

            if (state.status == DeviceStatus.LOCKED && activity != null) {
                LaunchedEffect(Unit) {
                    viewModel.recordPolicyResult(policyController.enforceLockedMode(activity))
                }
                LockOverlay(
                    state = state,
                    onEmergencyCall = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                    },
                    onGraceWindow = viewModel::requestGraceWindow
                )
            }
        }
    }
}

@Composable
private fun ActiveDashboard(
    state: DeviceLeaseUiState,
    onSimulatePayment: () -> Unit,
    onForceOverdue: () -> Unit
) {
    val statusColor = when (state.status) {
        DeviceStatus.ACTIVE -> EmeraldActive
        DeviceStatus.WARNING -> AmberWarning
        DeviceStatus.LOCKED -> VividCrimson
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SecurePay",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Customer financing status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = statusColor.copy(alpha = 0.14f),
                contentColor = statusColor,
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = state.status.name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = statusColor)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Next payment window",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = state.countdownText,
                    style = MaterialTheme.typography.displayMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                LinearProgressIndicator(
                    progress = { state.paymentProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = EmeraldActive,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricLabel("Balance", state.formattedBalance)
                    MetricLabel("Paid", "${state.paidPercent}%")
                }
                Button(
                    onClick = onSimulatePayment,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Simulate Payment Integration")
                }
                OutlinedButton(
                    onClick = onForceOverdue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Force Overdue Test")
                }
            }
        }
    }
}

@Composable
private fun MetricLabel(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LockOverlay(
    state: DeviceLeaseUiState,
    onEmergencyCall: () -> Unit,
    onGraceWindow: () -> Unit
) {
    BackHandler(enabled = true) {
        // Intentionally consume back while the DPC lock overlay is active.
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.98f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(84.dp),
                color = VividCrimson,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = VividCrimson,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Device Locked",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your SecurePay account is overdue. Standard navigation and back actions are blocked until a payment or approved grace window is applied.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.lastPolicyResult?.let {
                Text(
                    text = it.message,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ElevatedButton(
                onClick = onEmergencyCall,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Emergency Calls")
            }
            Button(
                onClick = onGraceWindow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Timer, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("5-Minute Grace Window Request")
            }
        }
    }
}
