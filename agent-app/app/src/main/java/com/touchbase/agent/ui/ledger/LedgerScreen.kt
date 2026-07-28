package com.touchbase.agent.ui.ledger

import android.app.Activity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.touchbase.agent.R
import com.touchbase.agent.data.model.Account
import com.touchbase.agent.data.model.LedgerEntry
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.components.SecurePayBottomNavBar
import com.touchbase.agent.ui.navigation.Screen
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.isLight
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    repository: SecurePayRepository?,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToMore: () -> Unit,
    onNavigateToCustomerPayments: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    var error by remember { mutableStateOf<String?>(null) }
    var methodFilter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (isPreview) {
            entries = listOf(
                LedgerEntry(id = "1", accountId = "a1", customerName = "John Doe", amount = 1500, method = "mobile_money", reference = "TRX123", dateEpochMillis = System.currentTimeMillis()),
                LedgerEntry(id = "2", accountId = "a2", customerName = "Jane Smith", amount = 500, method = "cash", dateEpochMillis = System.currentTimeMillis() - 86400000)
            )
            accounts = listOf(
                Account(id = "a1", customerName = "John Doe", phoneNumber = "+233501234567", totalLoanAmount = 120000, amountPaid = 1500, remainingBalance = 118500, termDays = 90, dailyRate = 500, nextPaymentDueEpochMillis = System.currentTimeMillis() + 86400000L * 3),
                Account(id = "a2", customerName = "Jane Smith", phoneNumber = "+233509876543", totalLoanAmount = 80000, amountPaid = 500, remainingBalance = 79500, termDays = 60, dailyRate = 300, nextPaymentDueEpochMillis = System.currentTimeMillis() + 86400000L * 10)
            )
            isLoading = false
            return
        }
        isLoading = true
        scope.launch {
            val ledgerResult = repository?.listLedger(methodFilter)
            val accountsResult = repository?.listAccounts()
            isLoading = false
            ledgerResult?.fold(
                onSuccess = { entries = it },
                onFailure = { error = it.message }
            )
            accountsResult?.fold(
                onSuccess = { accounts = it },
                onFailure = { /* ignore */ }
            )
        }
    }

    LaunchedEffect(methodFilter) { load() }

    val totalCollected = entries.sumOf { it.amount }
    val mobileMoneyCollected = entries.filter { it.method == "mobile_money" }.sumOf { it.amount }

    // Group entries by accountId and join account info
    val grouped = entries.groupBy { it.accountId }.mapNotNull { (accountId, list) ->
        val acc = accounts.find { it.id == accountId }
        val totalPaid = list.sumOf { it.amount }
        val progress = if (acc != null && acc.totalLoanAmount > 0) {
            acc.amountPaid.toFloat() / acc.totalLoanAmount.toFloat()
        } else 0f
        CustomerGroup(
            accountId = accountId,
            customerName = acc?.customerName ?: list.firstOrNull()?.customerName ?: "Unknown",
            totalPaid = totalPaid,
            progress = progress,
            account = acc
        )
    }.sortedByDescending { it.totalPaid }

    val view = LocalView.current
    val backgroundColor = MaterialTheme.colorScheme.background

    if (!isPreview) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = backgroundColor.isLight()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Payment Ledger", color = MaterialTheme.colorScheme.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    scrolledContainerColor = backgroundColor
                )
            )
        },
        bottomBar = {
            SecurePayBottomNavBar(
                selectedTab = 3,
                onHomeClick = onNavigateToHome,
                onCustomersClick = onNavigateToCustomers,
                onInventoryClick = onNavigateToInventory,
                onLedgerClick = {},
                onMoreClick = onNavigateToMore
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Collections", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatAmount(totalCollected), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Box(
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    LinearProgressIndicator(
                        progress = if (totalCollected > 0) (mobileMoneyCollected.toFloat() / totalCollected.toFloat()).coerceIn(0f, 1f) else 0f,
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${grouped.size} customer${if (grouped.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Mobile money ${formatAmount(mobileMoneyCollected)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                val filters = listOf(
                    null to "All",
                    "mobile_money" to "Mobile Money",
                    "cash" to "Cash",
                    "bank_transfer" to "Bank"
                )
                filters.forEach { (filterValue, label) ->
                    val isSelected = methodFilter == filterValue
                    FilterChip(
                        selected = isSelected,
                        onClick = { methodFilter = filterValue },
                        label = { Text(label, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        shape = RoundedCornerShape(360.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
            } else if (grouped.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No payments recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    grouped.forEach { group ->
                        CustomerLedgerCard(
                            group = group,
                            onClick = { group.account?.id?.let { onNavigateToCustomerPayments(it) } }
                        )
                    }
                }
            }
        }
    }
}

data class CustomerGroup(
    val accountId: String,
    val customerName: String,
    val totalPaid: Int,
    val progress: Float,
    val account: Account?
)

@Composable
private fun CustomerLedgerCard(group: CustomerGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.customerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatAmount(group.totalPaid),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = group.progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val acc = group.account
                val progressText = if (acc != null && acc.totalLoanAmount > 0) {
                    "${formatAmount(acc.amountPaid)} / ${formatAmount(acc.totalLoanAmount)}"
                } else {
                    formatAmount(group.totalPaid)
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (acc != null) {
                    Text(
                        text = "${(group.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdf.format(Date(epochMillis))
    } catch (_: Exception) {
        "—"
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LedgerScreenPreview() {
    SecurePayAgentTheme {
        LedgerScreen(
            repository = null,
            onBack = {},
            onNavigateToHome = {},
            onNavigateToCustomers = {},
            onNavigateToInventory = {},
            onNavigateToMore = {}
        )
    }
}
