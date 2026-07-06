package com.touchbase.user.ui.dashboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.touchbase.user.admin.SecurityChecker
import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.formatCentsAsCurrency
import com.touchbase.user.ui.DeviceUiState
import com.touchbase.user.ui.theme.Amber
import com.touchbase.user.ui.theme.Charcoal
import com.touchbase.user.ui.theme.CharcoalElevated
import com.touchbase.user.ui.theme.CharcoalSurfaceVariant
import com.touchbase.user.ui.theme.Crimson
import com.touchbase.user.ui.theme.Emerald
import com.touchbase.user.ui.theme.TextPrimary
import com.touchbase.user.ui.theme.TextSecondary

private const val CUSTOMER_APP_PERMISSION_REQUEST = 8801

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DeviceUiState,
    onRefresh: () -> Unit,
    onMessageShown: () -> Unit,
    onViewPayments: () -> Unit,
    onCheckUpdates: () -> Unit,
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
        containerColor = Charcoal,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TB User", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Device financing dashboard", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Charcoal)
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Emerald)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isOffline) OfflineBanner()
            securityReport?.takeIf { it.shouldLock }?.let { SecurityWarningBanner(it) }

            HeroStatusCard(state)

            state.account?.let { account ->
                LoanSummaryCard(account)
                if (account.isStolen) StolenTrackingCard()
            }

            ActionGrid(
                state = state,
                onRefresh = onRefresh,
                onViewPayments = onViewPayments,
                onCheckUpdates = onCheckUpdates
            )

            PermissionHealthCard()
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HeroStatusCard(state: DeviceUiState) {
    val (title, subtitle, icon, accent) = when (state.status) {
        DeviceStatus.ACTIVE -> Quad("Device Active", "Your loan device is currently enabled.", Icons.Filled.VerifiedUser, Emerald)
        DeviceStatus.WARNING -> Quad("Payment Due Soon", "Please keep your next payment on time.", Icons.Filled.NotificationsActive, Amber)
        DeviceStatus.LOCKED -> Quad("Device Locked", "Sync after payment or dealer unlock to restore access.", Icons.Filled.Lock, Crimson)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.24f), CharcoalElevated)))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
                if (!state.releaseApproved) {
                    Text(
                        text = if (state.status == DeviceStatus.LOCKED) "Locked until account is cleared" else "Next due: ${state.remaining.format()}",
                        color = accent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LoanSummaryCard(account: LoanAccount) {
    val progress = account.repaymentProgress
    InfoCard(title = "Loan Summary", icon = Icons.Filled.CreditCard, accent = Emerald) {
        Text(
            text = formatCentsAsCurrency(account.remainingBalanceCents, account.currencyCode),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text("Remaining balance", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Emerald,
            trackColor = CharcoalSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Plan", account.planName)
            Metric("Paid", formatCentsAsCurrency(account.amountPaidCents, account.currencyCode))
            Metric("Daily", formatCentsAsCurrency(account.dailyRateCents, account.currencyCode))
        }
    }
}

@Composable
private fun ActionGrid(
    state: DeviceUiState,
    onRefresh: () -> Unit,
    onViewPayments: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onRefresh,
            enabled = !state.isProcessingPayment,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color(0xFF07130F))
        ) {
            if (state.isProcessingPayment) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF07130F))
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sync Status Now", fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryAction(
                text = "Payments",
                icon = Icons.Filled.Payments,
                modifier = Modifier.weight(1f),
                onClick = onViewPayments
            )
            SecondaryAction(
                text = "Updates",
                icon = Icons.Filled.SystemUpdate,
                modifier = Modifier.weight(1f),
                onClick = onCheckUpdates
            )
        }
    }
}

@Composable
private fun SecondaryAction(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermissionHealthCard() {
    val context = LocalContext.current
    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    InfoCard(
        title = if (missing.isEmpty()) "Permissions Ready" else "Permissions Need Attention",
        icon = Icons.Filled.Security,
        accent = if (missing.isEmpty()) Emerald else Amber
    ) {
        Text(
            text = if (missing.isEmpty()) {
                "Location, background service, and notification permissions are available. In Device Owner mode these are granted silently by policy."
            } else {
                "Some runtime permissions are missing. Tracking may not upload GPS until they are granted."
            },
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), CUSTOMER_APP_PERMISSION_REQUEST)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200))
                ) { Text("Grant") }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) { Text("Settings") }
            }
        }
    }
}

@Composable
private fun StolenTrackingCard() {
    InfoCard(title = "Stolen Tracking Active", icon = Icons.Filled.LocationOn, accent = Crimson) {
        Text(
            "The phone is in recovery mode. It will keep trying to upload location pings whenever GPS and internet are available.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier.fillMaxWidth().background(Amber.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.Security, contentDescription = null, tint = Amber)
        Text("Offline - showing cached account state", color = Amber, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SecurityWarningBanner(report: SecurityChecker.SecurityReport) {
    val reasons = buildList {
        if (report.isRooted) add("root")
        if (report.isTampered) add("tamper")
        if (report.isEmulator) add("emulator")
        if (report.isDebuggable) add("debug")
    }.joinToString(", ")
    InfoCard(title = "Security Warning", icon = Icons.Filled.Security, accent = Crimson) {
        Text("Security check detected: $reasons", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoCard(title: String, icon: ImageVector, accent: Color, content: @Composable Column.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            content()
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
