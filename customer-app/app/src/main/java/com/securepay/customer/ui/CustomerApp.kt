package com.securepay.customer.ui

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.securepay.customer.domain.DeviceStatus
import com.securepay.customer.ui.theme.CharcoalSurface
import com.securepay.customer.ui.theme.CharcoalSurfaceHigh
import com.securepay.customer.ui.theme.DeepCharcoal
import com.securepay.customer.ui.theme.EmeraldGreen
import com.securepay.customer.ui.theme.MutedText
import com.securepay.customer.ui.theme.SignalAmber
import com.securepay.customer.ui.theme.VividCrimson
import com.securepay.customer.viewmodel.CustomerDashboardUiState
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDashboardScreen(
    uiState: CustomerDashboardUiState,
    onSimulatePayment: () -> Unit,
    onRequestGraceWindow: () -> Unit,
    onEmergencyCall: () -> Unit
) {
    Scaffold(
        containerColor = DeepCharcoal,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SecurePay",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepCharcoal,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepCharcoal)
                .padding(padding)
                .safeDrawingPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                StatusHeader(uiState)
                CountdownCard(uiState, onSimulatePayment)
                FinanceCard(uiState)
                DeviceIdentityCard(uiState)
            }

            if (uiState.status == DeviceStatus.LOCKED) {
                LockOverlay(
                    uiState = uiState,
                    onEmergencyCall = onEmergencyCall,
                    onRequestGraceWindow = onRequestGraceWindow
                )
            }
        }
    }
}

@Composable
private fun StatusHeader(uiState: CustomerDashboardUiState) {
    val (label, color) = when (uiState.status) {
        DeviceStatus.ACTIVE -> "Active" to EmeraldGreen
        DeviceStatus.WARNING -> "Payment Due Soon" to SignalAmber
        DeviceStatus.LOCKED -> "Locked" to VividCrimson
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Hello, ${uiState.customerName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Contract ${uiState.contractId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
        }
        Surface(
            color = color.copy(alpha = 0.14f),
            contentColor = color,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CountdownCard(
    uiState: CustomerDashboardUiState,
    onSimulatePayment: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Access countdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = formatDuration(uiState.remainingMillis),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = when (uiState.status) {
                    DeviceStatus.ACTIVE -> EmeraldGreen
                    DeviceStatus.WARNING -> SignalAmber
                    DeviceStatus.LOCKED -> VividCrimson
                },
                trackColor = CharcoalSurfaceHigh
            )

            Button(
                onClick = onSimulatePayment,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen,
                    contentColor = DeepCharcoal
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Payments, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Simulate Payment Integration")
            }
        }
    }
}

@Composable
private fun FinanceCard(uiState: CustomerDashboardUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Loan balance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FinanceMetric(
                    label = "Outstanding",
                    value = formatCurrency(uiState.outstandingBalanceCents)
                )
                FinanceMetric(
                    label = "Financed",
                    value = formatCurrency(uiState.financedAmountCents)
                )
            }
        }
    }
}

@Composable
private fun FinanceMetric(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MutedText)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeviceIdentityCard(uiState: CustomerDashboardUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Device identity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = "IMEI ${uiState.imei}", color = MutedText)
        }
    }
}

@Composable
private fun LockOverlay(
    uiState: CustomerDashboardUiState,
    onEmergencyCall: () -> Unit,
    onRequestGraceWindow: () -> Unit
) {
    BackHandler(enabled = true) {
        // Back is intentionally consumed while a financed device is locked.
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .background(VividCrimson.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = VividCrimson
                )
            }

            Text(
                text = "Device locked",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = VividCrimson,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Payment access for contract ${uiState.contractId} is overdue. Standard back navigation and lock-task escape paths are blocked while SecurePay is enforced.",
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText,
                textAlign = TextAlign.Center
            )

            CircularProgressIndicator(color = VividCrimson)

            Button(
                onClick = onEmergencyCall,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = DeepCharcoal
                )
            ) {
                Icon(imageVector = Icons.Default.Phone, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Emergency Calls")
            }

            Button(
                onClick = onRequestGraceWindow,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VividCrimson,
                    contentColor = Color.White
                )
            ) {
                Text("5-Minute Grace Window Request")
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (days > 0) {
        "%dd %02dh %02dm %02ds".format(days, hours, minutes, seconds)
    } else {
        "%02dh %02dm %02ds".format(hours, minutes, seconds)
    }
}

private fun formatCurrency(cents: Long): String {
    return NumberFormat.getCurrencyInstance(Locale.US).format(cents / 100.0)
}
