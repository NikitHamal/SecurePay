package com.touchbase.user.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.user.admin.SecurityChecker
import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.formatCentsAsCurrency
import com.touchbase.user.ui.DeviceUiState
import com.touchbase.user.ui.components.StatusBadge
import com.touchbase.user.ui.components.statusColor
import com.touchbase.user.ui.theme.Amber
import com.touchbase.user.ui.theme.CharcoalElevated
import com.touchbase.user.ui.theme.Crimson
import com.touchbase.user.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DeviceUiState,
    onRefresh: () -> Unit,
    onMessageShown: () -> Unit,
    onViewPayments: () -> Unit,
    securityReport: SecurityChecker.SecurityReport? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("SecurePay", fontWeight = FontWeight.Bold) },
                actions = {
                    OutlinedButton(
                        onClick = onViewPayments,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("History", fontWeight = FontWeight.Medium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { inner ->
        val account = state.account
        if (state.isLoading || account == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Loading account\u2026", color = TextSecondary)
                if (state.isOffline) {
                    Spacer(Modifier.height(8.dp))
                    OfflineBanner()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.isOffline) {
                    OfflineBanner()
                }
                if (securityReport != null && securityReport.hasWarnings) {
                    SecurityWarningBanner(securityReport)
                }
                Text(
                    text = "Hello, ${account.customerName.split(" ").firstOrNull() ?: "Customer"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                CountdownCard(state = state, account = account)
                BalanceCard(account = account)
                PaymentTrigger(
                    isProcessing = state.isProcessingPayment,
                    onClick = onRefresh
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CountdownCard(state: DeviceUiState, account: LoanAccount) {
    val accent by animateColorAsState(
        targetValue = statusColor(state.status),
        label = "statusAccent"
    )
    val progress by animateFloatAsState(
        targetValue = account.repaymentProgress,
        label = "repaymentProgress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.deviceModel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusBadge(status = state.status)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (state.status == DeviceStatus.LOCKED) "PAYMENT OVERDUE"
                else "TIME UNTIL NEXT PAYMENT",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.remaining.format(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                color = accent,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${(progress * 100).toInt()}% of plan repaid",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun BalanceCard(account: LoanAccount) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Remaining Balance",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatCentsAsCurrency(account.remainingBalanceCents, account.currencyCode),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricColumn(
                    label = "Plan",
                    value = account.planName,
                    modifier = Modifier.weight(1f)
                )
                MetricColumn(
                    label = "Daily Rate",
                    value = formatCentsAsCurrency(account.dailyRateCents, account.currencyCode),
                    modifier = Modifier.weight(1f)
                )
                MetricColumn(
                    label = "Paid",
                    value = formatCentsAsCurrency(account.amountPaidCents, account.currencyCode),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PaymentTrigger(isProcessing: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isProcessing,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(Icons.Filled.Bolt, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Check for Updates", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Amber.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "Offline \u2014 showing cached data",
            style = MaterialTheme.typography.bodySmall,
            color = Amber,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SecurityWarningBanner(report: SecurityChecker.SecurityReport) {
    val bannerColor = if (report.shouldLock) Crimson else Amber
    val bannerText = when {
        report.isRooted -> "SECURITY: Rooted device detected \u2014 device locked for your protection"
        report.isTampered -> "SECURITY: App integrity check failed \u2014 device locked"
        report.isEmulator -> "Emulator environment detected"
        report.isDebuggable -> "Debug build \u2014 not for production use"
        else -> "Security warning detected"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bannerColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = bannerColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = bannerText,
            style = MaterialTheme.typography.bodySmall,
            color = bannerColor,
            fontWeight = FontWeight.Medium
        )
    }
}
