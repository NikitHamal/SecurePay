package com.touchbase.agent.ui.ledger

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.touchbase.agent.data.model.Account
import com.touchbase.agent.data.model.AccountStatus
import com.touchbase.agent.data.model.LedgerEntry
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.components.SecurePayBottomNavBar
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.isLight
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Brand success green (Touch Base palette). */
private val SuccessGreen = Color(0xFF10B981)

/** Brand warning amber (Touch Base palette). */
private val WarningAmber = Color(0xFFF59E0B)

/**
 * One row in the grouped ledger: every payment in the current (method-filtered)
 * set rolled up per customer, joined with the account record for the loan
 * progress + phone number the detail screen needs.
 */
private data class CustomerLedgerGroup(
    val accountId: String,
    val customerName: String,
    val phoneNumber: String,
    val totalCollected: Int,
    val paymentCount: Int,
    val lastPaymentEpoch: Long,
    val amountPaid: Int,
    val totalLoan: Int,
    val remainingBalance: Int,
    val status: AccountStatus,
    val releaseApproved: Boolean,
    val hasAccount: Boolean
)

private fun statusTint(group: CustomerLedgerGroup): Color = when {
    !group.hasAccount -> Color.Unspecified
    group.releaseApproved || group.remainingBalance <= 0 -> SuccessGreen
    group.status == AccountStatus.LOCKED -> Color.Unspecified // resolved below via theme error
    group.status == AccountStatus.WARNING -> WarningAmber
    else -> Color.Unspecified
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    repository: SecurePayRepository?,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToMore: () -> Unit,
    onCustomerClick: (accountId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    var error by remember { mutableStateOf<String?>(null) }
    // Canonical, server-side method strings (uppercase). Filtering is done in
    // memory so we never depend on the query-param casing the worker accepts.
    var methodFilter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (isPreview) {
            val now = System.currentTimeMillis()
            entries = listOf(
                LedgerEntry(id = "1", accountId = "a1", customerName = "Ama Mensah", amount = 150000, method = "MOBILE_MONEY", reference = "MM123", dateEpochMillis = now),
                LedgerEntry(id = "2", accountId = "a1", customerName = "Ama Mensah", amount = 150000, method = "CASH", dateEpochMillis = now - 86_400_000L),
                LedgerEntry(id = "3", accountId = "a2", customerName = "Kofi Boateng", amount = 200000, method = "MOBILE_MONEY", reference = "MM456", dateEpochMillis = now - 3_600_000L)
            )
            accounts = listOf(
                Account(id = "a1", customerName = "Ama Mensah", phoneNumber = "0244111222", totalLoanAmount = 3_000_000, amountPaid = 1_200_000, remainingBalance = 1_800_000, dailyRate = 60_000, termDays = 30, nextPaymentDueEpochMillis = now + 86_400_000L, status = AccountStatus.ACTIVE),
                Account(id = "a2", customerName = "Kofi Boateng", phoneNumber = "0555333444", totalLoanAmount = 4_500_000, amountPaid = 4_500_000, remainingBalance = 0, termDays = 45, status = AccountStatus.ACTIVE, releaseApproved = true)
            )
            return
        }
        isLoading = true
        error = null
        scope.launch {
            val ledgerDeferred = async { repository?.listLedger() }
            val accountsDeferred = async { repository?.listAccounts() }
            val ledgerResult = ledgerDeferred.await()
            val accountsResult = accountsDeferred.await()
            isLoading = false
            if (ledgerResult == null) return@launch
            ledgerResult.fold(
                onSuccess = { entries = it },
                onFailure = { error = it.message }
            )
            // Account totals are enrichment only: if they fail we still show the
            // grouped ledger, just without the paid/total progress bars.
            accountsResult?.onSuccess { accounts = it }
        }
    }

    LaunchedEffect(methodFilter) { load() }

    val filteredEntries = if (methodFilter == null) {
        entries
    } else {
        entries.filter { it.method == methodFilter }
    }
    val totalCollected = filteredEntries.sumOf { it.amount }
    val mobileMoneyCollected = filteredEntries.filter { it.method == "MOBILE_MONEY" }.sumOf { it.amount }

    val accountById = remember(accounts) { accounts.associateBy { it.id } }
    val groups = remember(filteredEntries, accountById) {
        filteredEntries
            .groupBy { it.accountId }
            .map { (accountId, list) ->
                val account = accountById[accountId]
                val sorted = list.sortedByDescending { it.dateEpochMillis }
                CustomerLedgerGroup(
                    accountId = accountId,
                    customerName = account?.customerName?.takeIf { it.isNotBlank() }
                        ?: list.firstOrNull()?.customerName?.takeIf { it.isNotBlank() }
                        ?: "Unknown customer",
                    phoneNumber = account?.phoneNumber.orEmpty(),
                    totalCollected = list.sumOf { it.amount },
                    paymentCount = list.size,
                    lastPaymentEpoch = sorted.firstOrNull()?.dateEpochMillis ?: 0L,
                    amountPaid = account?.amountPaid ?: 0,
                    totalLoan = account?.totalLoanAmount ?: 0,
                    remainingBalance = account?.remainingBalance ?: 0,
                    status = account?.status ?: AccountStatus.ACTIVE,
                    releaseApproved = account?.releaseApproved ?: false,
                    hasAccount = account != null
                )
            }
            .sortedByDescending { it.lastPaymentEpoch }
    }

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
                        val customerWord = if (groups.size == 1) "customer" else "customers"
                        val paymentWord = if (filteredEntries.size == 1) "payment" else "payments"
                        Text("${groups.size} $customerWord · ${filteredEntries.size} $paymentWord", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "MOBILE_MONEY" to "Mobile Money",
                    "CASH" to "Cash",
                    "BANK" to "Bank"
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (error != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
            } else if (groups.isEmpty()) {
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
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups, key = { it.accountId.ifBlank { it.customerName } }) { group ->
                        CustomerLedgerRow(group = group, onClick = { onCustomerClick(group.accountId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerLedgerRow(group: CustomerLedgerGroup, onClick: () -> Unit) {
    val tint = when {
        !group.hasAccount -> MaterialTheme.colorScheme.onSurfaceVariant
        group.releaseApproved || group.remainingBalance <= 0 -> SuccessGreen
        group.status == AccountStatus.LOCKED -> MaterialTheme.colorScheme.error
        group.status == AccountStatus.WARNING -> WarningAmber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val progress = if (group.totalLoan > 0) {
        (group.amountPaid.toFloat() / group.totalLoan.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percent = if (group.totalLoan > 0) (progress * 100f).toInt() else 0

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.customerName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (group.hasAccount) group.displayStatusText() else "${group.paymentCount} ${if (group.paymentCount == 1) "payment" else "payments"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tint
                            )
                            if (group.hasAccount) {
                                Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${group.paymentCount} ${if (group.paymentCount == 1) "payment" else "payments"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatAmount(group.totalCollected),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Last ${formatRelative(group.lastPaymentEpoch)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (group.hasAccount) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (group.remainingBalance <= 0) SuccessGreen else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${formatAmount(group.amountPaid)} of ${formatAmount(group.totalLoan)} paid",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tint
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "View payments",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun CustomerLedgerGroup.displayStatusText(): String = if (releaseApproved) {
    "Paid off"
} else {
    when (status) {
        AccountStatus.ACTIVE -> "Active"
        AccountStatus.WARNING -> "Due soon"
        AccountStatus.LOCKED -> "Overdue"
        AccountStatus.STOLEN -> "Stolen"
    }
}

private fun formatRelative(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L
    val diff = now - epochMillis
    return when {
        diff < 0L -> formatDate(epochMillis)
        diff < dayMs -> "today"
        diff < 2L * dayMs -> "yesterday"
        else -> formatDate(epochMillis)
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
            onNavigateToMore = {},
            onCustomerClick = {}
        )
    }
}
