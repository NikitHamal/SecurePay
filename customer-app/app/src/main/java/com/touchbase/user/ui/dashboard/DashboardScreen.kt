package com.touchbase.user.ui.dashboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import com.touchbase.user.data.model.AdModel
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.repository.AdRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.touchbase.user.R
import com.touchbase.user.admin.SecurityChecker
import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.formatCentsAsCurrency
import com.touchbase.user.ui.DeviceUiState
import com.touchbase.user.ui.components.AdSlideView
import com.touchbase.user.ui.components.CustomerBottomBar
import com.touchbase.user.ui.theme.Amber
import com.touchbase.user.ui.theme.Charcoal
import com.touchbase.user.ui.theme.CharcoalElevated
import com.touchbase.user.ui.theme.CharcoalSurfaceVariant
import com.touchbase.user.ui.theme.Crimson
import com.touchbase.user.ui.theme.Gold
import com.touchbase.user.ui.theme.TextPrimary
import com.touchbase.user.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CUSTOMER_APP_PERMISSION_REQUEST = 8801

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DeviceUiState,
    onRefresh: () -> Unit,
    onMessageShown: () -> Unit,
    onViewPayments: () -> Unit,
    onPayNow: () -> Unit,
    onCheckUpdates: () -> Unit,
    onMore: () -> Unit,
    onAccount: () -> Unit = {},
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
        bottomBar = {
            CustomerBottomBar(
                selected = "home",
                onHome = {},
                onPayments = onViewPayments,
                onMore = onMore,
                onAccount = onAccount
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.touchbase_logo),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Touch Base", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Device financing", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
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
                CircularProgressIndicator(color = Gold)
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
                MyDeviceCard(account)
                LoanAgreementCard(account)
                if (account.isStolen) StolenTrackingCard()
            }

            // Ads Section - cached locally, refreshed on Sync Status
            val showAds = remember { true }
            var adRefreshTrigger by remember { mutableIntStateOf(0) }

            ActionGrid(
                state = state,
                onRefresh = { onRefresh(); adRefreshTrigger++ },
                onPayNow = onPayNow,
                onViewPayments = onViewPayments,
                onCheckUpdates = onCheckUpdates
            )
            
            if (showAds) {
                AdSlideSection(refreshTrigger = adRefreshTrigger)
            }
            
            PermissionHealthCard()
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HeroStatusCard(state: DeviceUiState) {
    val (title, subtitle, icon, accent) = when (state.status) {
        DeviceStatus.ACTIVE -> Quad("Device Active", "Your loan device is currently enabled.", Icons.Filled.VerifiedUser, Gold)
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
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
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
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, softWrap = false)
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

/**
 * "My Device" — the self-service card the client specified:
 * device name, paid, remaining, next-payment date and repayment progress.
 */
@Composable
private fun MyDeviceCard(account: LoanAccount) {
    val progress = account.repaymentProgress
    InfoCard(title = "My Device", icon = Icons.Filled.Smartphone, accent = Gold) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Device",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = account.deviceModel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2
                )
            }
            LoanStatusChip(account)
        }

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Paid", formatCentsAsCurrency(account.amountPaidCents, account.currencyCode), Modifier.weight(1f))
            Metric("Remaining", formatCentsAsCurrency(account.remainingBalanceCents, account.currencyCode), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Gold,
            trackColor = CharcoalSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${(progress * 100).toInt()}% of ${formatCentsAsCurrency(account.totalLoanAmountCents, account.currencyCode)} repaid",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Next payment", formatLoanDate(account.nextPaymentDueEpochMillis), Modifier.weight(1f))
            Metric("Daily rate", formatCentsAsCurrency(account.dailyRateCents, account.currencyCode), Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoanStatusChip(account: LoanAccount) {
    val (label, tone) = when {
        account.releaseApproved -> "Fully paid" to Gold
        account.isStolen || account.lockedByDealer -> "Locked" to Crimson
        account.remainingBalanceCents <= 0 -> "Fully paid" to Gold
        else -> "Active" to Gold
    }
    Card(
        shape = RoundedCornerShape(360.dp),
        colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.16f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tone,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Signed loan agreement on demand ("loan agreement details" self-service). */
@Composable
private fun LoanAgreementCard(account: LoanAccount) {
    var showAgreement by remember { mutableStateOf(false) }

    InfoCard(title = "Loan Agreement", icon = Icons.Filled.Description, accent = Gold) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Plan", account.planName, Modifier.weight(1f))
            Metric("Term", "${account.termDays} days", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Total", formatCentsAsCurrency(account.totalLoanAmountCents, account.currencyCode), Modifier.weight(1f))
            Metric(
                "Started",
                if (account.createdAtEpochMillis > 0) formatLoanDate(account.createdAtEpochMillis) else "—",
                Modifier.weight(1f)
            )
        }
        if (!account.agreementText.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { showAgreement = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = Gold)
                Spacer(Modifier.width(8.dp))
                Text("View signed agreement", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showAgreement && !account.agreementText.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showAgreement = false },
            confirmButton = {
                TextButton(onClick = { showAgreement = false }) {
                    Text("Close", color = Gold, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text("Device Financing Agreement", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    account.consentAt?.takeIf { it > 0 }?.let { consentAt ->
                        Text(
                            text = "Signed on ${formatLoanDate(consentAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        text = account.agreementText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            },
            containerColor = CharcoalElevated
        )
    }
}

private fun formatLoanDate(epochMillis: Long): String = runCatching {
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(epochMillis))
}.getOrDefault("—")

@Composable
private fun ActionGrid(
    state: DeviceUiState,
    onRefresh: () -> Unit,
    onPayNow: () -> Unit,
    onViewPayments: () -> Unit,
    onCheckUpdates: () -> Unit,
    securityReport: SecurityChecker.SecurityReport? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onPayNow,
            enabled = !state.isProcessingPayment,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0B0B0C))
        ) {
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Pay with Mobile Money", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }

        OutlinedButton(
            onClick = onRefresh,
            enabled = !state.isProcessingPayment,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) {
            if (state.isProcessingPayment) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Gold)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sync Status", fontWeight = FontWeight.SemiBold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryAction(
                text = "History",
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
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
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

    if (missing.isEmpty()) return

    InfoCard(
        title = "Permissions Need Attention",
        icon = Icons.Filled.Security,
        accent = Amber
    ) {
        Text(
            text = "Some runtime permissions are missing. Tracking may not upload GPS until they are granted.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
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
private fun InfoCard(title: String, icon: ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
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
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * Ads section with slide view.
 * Loads cached ads instantly on open. When the user taps "Sync Status"
 * (refreshTrigger changes), fetches fresh ads from the network and updates cache.
 */
@Composable
private fun AdSlideSection(refreshTrigger: Int = 0) {
    val context = LocalContext.current
    var ads by remember { mutableStateOf<List<AdModel>?>(null) }

    // Load cached ads instantly on first composition
    LaunchedEffect(Unit) {
        val repo = AdRepository(ApiModule.provideApi(), context)
        ads = repo.getCachedAds()
        // If no cache, fetch from network
        if (ads.isNullOrEmpty()) {
            ads = repo.getActiveAds().getOrNull() ?: emptyList()
        }
    }

    // Refresh from network when Sync Status is tapped
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            val repo = AdRepository(ApiModule.provideApi(), context)
            ads = repo.refreshAds().getOrNull() ?: emptyList()
        }
    }

    val loaded = ads
    if (loaded != null && loaded.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Ads",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (loaded == null) {
            AdSlideView(
                ads = listOf(
                    AdModel(
                        id = "loading",
                        title = "Loading offers…",
                        description = "",
                        isActive = true,
                        order = 0
                    )
                ),
                modifier = Modifier.fillMaxWidth(),
                autoScroll = false,
                scrollInterval = 5000L
            )
        } else {
            AdSlideView(
                ads = loaded,
                modifier = Modifier.fillMaxWidth(),
                autoScroll = true,
                scrollInterval = 5000L
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
