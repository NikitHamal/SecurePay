package com.touchbase.agent.ui.ledger

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.touchbase.agent.R
import com.touchbase.agent.data.model.Account
import com.touchbase.agent.data.model.LedgerEntry
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.components.SecurePayBottomNavBar
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.isLight
import kotlinx.coroutines.launch

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
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    var error by remember { mutableStateOf<String?>(null) }
    var methodFilter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
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

    fun load() {
        if (isPreview) {
            accounts = listOf(
                Account(
                    id = "acc-1",
                    customerName = "Daniel Sem",
                    phoneNumber = "+233 24 123 4567",
                    totalLoanAmount = 50000,
                    amountPaid = 35000,
                    remainingBalance = 15000,
                    dailyRate = 500,
                    nextPaymentDueEpochMillis = System.currentTimeMillis() + 86400000 * 3,
                    termDays = 30,
                    status = com.touchbase.agent.data.model.AccountStatus.ACTIVE
                ),
                Account(
                    id = "acc-2",
                    customerName = "Ama Serwaa",
                    phoneNumber = "+233 55 987 6543",
                    totalLoanAmount = 30000,
                    amountPaid = 8000,
                    remainingBalance = 22000,
                    dailyRate = 300,
                    nextPaymentDueEpochMillis = System.currentTimeMillis() + 86400000 * 10,
                    termDays = 30,
                    status = com.touchbase.agent.data.model.AccountStatus.ACTIVE
                )
            )
            entries = listOf(
                LedgerEntry(
                    id = "p1", accountId = "acc-1", customerName = "Daniel Sem",
                    amount = 20000, dateEpochMillis = System.currentTimeMillis() - 86400000 * 2,
                    method = "cash", reference = "CASH 001"
                ),
                LedgerEntry(
                    id = "p2", accountId = "acc-1", customerName = "Daniel Sem",
                    amount = 15000, dateEpochMillis = System.currentTimeMillis() - 86400000,
                    method = "mobile_money", reference = "TRX-778"
                ),
                LedgerEntry(
                    id = "p3", accountId = "acc-2", customerName = "Ama Serwaa",
                    amount = 8000, dateEpochMillis = System.currentTimeMillis() - 86400000 * 5,
                    method = "bank_transfer", reference = "BK-112"
                )
            )
            isLoading = false
            return
        }
        isLoading = true
        scope.launch {
            val accountsResult = repository?.listAccounts()
            val entriesResult = repository?.listLedger(methodFilter)
            isLoading = false
            accountsResult?.fold(
                onSuccess = { accounts = it },
                onFailure = { error = it.message }
            )
            entriesResult?.fold(
                onSuccess = { entries = it },
                onFailure = { /* ignore; accounts are primary */ }
            )
        }
    }

    LaunchedEffect(methodFilter) { load() }

    val totalCollected = entries.sumOf { it.amount }
    val mobileMoneyCollected = entries.filter { it.method == "mobile_money" }.sumOf { it.amount }

    // Client-side grouping: pair each account with its payments (filtered by method)
    val grouped = remember(accounts, entries, methodFilter) {
        accounts.mapNotNull { account ->
            val accountPayments = entries.filter { it.accountId == account.id }
            // If a method filter is active, only show accounts that have at least one
            // matching payment. If no filter, show all accounts with loans.
            if (methodFilter != null && accountPayments.isEmpty()) {
                null
            } else {
                account to accountPayments.sumOf { it.amount }
            }
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
            // Collections card — reframed as total collected across all users
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
                            androidx.compose.material3.Icon(
                                Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = if (totalCollected > 0) (mobileMoneyCollected.toFloat() / totalCollected.toFloat()).coerceIn(0f, 1f) else 0f,
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total from all users", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Mobile money ${formatAmount(mobileMoneyCollected)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Filter chips (keep from existing design)
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
                    androidx.compose.material3.Icon(
                        Icons.Filled.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No customers match this filter", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(grouped.size) { index ->
                        val (account, paymentsTotal) = grouped[index]
                        CustomerLedgerCard(
                            account = account,
                            paymentsTotal = paymentsTotal,
                            onClick = {
                                onNavigateToCustomerPayments(account.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerLedgerCard(
    account: Account,
    paymentsTotal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (account.totalLoanAmount > 0) {
        (account.amountPaid.toFloat() / account.totalLoanAmount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    )
        .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Loan ${formatAmount(account.totalLoanAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatAmount(account.amountPaid),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${String.format(java.util.Locale.getDefault(), "%.0f", progress * 100)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Remaining ${formatAmount(account.remainingBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Term ${account.termDays}d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
