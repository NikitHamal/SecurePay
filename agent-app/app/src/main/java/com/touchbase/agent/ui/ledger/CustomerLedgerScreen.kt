package com.touchbase.agent.ui.ledger

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.isLight
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SuccessGreen = Color(0xFF10B981)
private val WarningAmber = Color(0xFFF59E0B)

/**
 * Per-customer view reached by tapping a customer row on the Payment Ledger.
 * Shows the loan repayment progress, the full payment history for that account,
 * and one-tap actions to call the customer or send an encouraging payment
 * reminder over SMS / WhatsApp. All three actions use plain VIEW/SENDTO/DIAL
 * intents (the ContactUsScreen precedent) so no runtime permission is required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLedgerScreen(
    accountId: String,
    repository: SecurePayRepository?,
    onBack: () -> Unit,
    onOpenAccount: (accountId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var account by remember { mutableStateOf<Account?>(null) }
    var payments by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    var error by remember { mutableStateOf<String?>(null) }

    if (isPreview) {
        val now = System.currentTimeMillis()
        account = Account(
            id = accountId,
            customerName = "Ama Mensah",
            phoneNumber = "0244111222",
            totalLoanAmount = 3_000_000,
            amountPaid = 1_200_000,
            remainingBalance = 1_800_000,
            dailyRate = 60_000,
            termDays = 30,
            nextPaymentDueEpochMillis = now + 86_400_000L,
            status = AccountStatus.ACTIVE
        )
        payments = listOf(
            LedgerEntry(id = "1", accountId = accountId, customerName = "Ama Mensah", amount = 150_000, method = "MOBILE_MONEY", reference = "MM123", dateEpochMillis = now),
            LedgerEntry(id = "2", accountId = accountId, customerName = "Ama Mensah", amount = 150_000, method = "CASH", dateEpochMillis = now - 86_400_000L)
        )
    }

    fun load() {
        if (isPreview) return
        isLoading = true
        error = null
        scope.launch {
            val accountDeferred = async { repository?.getAccount(accountId) }
            val ledgerDeferred = async { repository?.listLedger(accountId = accountId) }
            val accountResult = accountDeferred.await()
            val ledgerResult = ledgerDeferred.await()
            isLoading = false
            if (accountResult == null) return@launch
            accountResult.fold(
                onSuccess = { account = it },
                onFailure = { error = it.message }
            )
            ledgerResult?.fold(
                onSuccess = { payments = it.sortedByDescending { e -> e.dateEpochMillis } },
                onFailure = { if (error == null) error = it.message }
            )
        }
    }

    LaunchedEffect(accountId) { load() }

    fun open(intent: Intent, enabled: Boolean) {
        if (!enabled) {
            scope.launch { snackbar.showSnackbar("No phone number on file for this customer") }
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { scope.launch { snackbar.showSnackbar("No compatible app is available") } }
    }

    val acc = account
    val phone = acc?.phoneNumber.orEmpty()
    val phonePresent = phone.isNotBlank()
    val reminderBody = acc?.let { buildReminderText(it) }.orEmpty()

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
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(acc?.customerName ?: "Customer payments", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    if (acc != null) {
                        IconButton(onClick = { onOpenAccount(accountId) }) {
                            Icon(Icons.Filled.Person, contentDescription = "Open account", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }
        if (error != null && acc == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(error ?: "Could not load customer", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (acc != null) {
                item(key = "progress") { ProgressCard(acc) }
                item(key = "actions") {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Reach out",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            ActionRow(
                                icon = Icons.Filled.Call,
                                title = "Call customer",
                                subtitle = if (phonePresent) phone else "No phone number on file"
                            ) {
                                open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")), phonePresent)
                            }
                            ActionRow(
                                icon = Icons.Filled.Sms,
                                title = "Send payment reminder (SMS)",
                                subtitle = "Pre-filled, encouraging text message"
                            ) {
                                open(
                                    Intent(Intent.ACTION_SENDTO, Uri.parse("sms:$phone?body=${Uri.encode(reminderBody)}")),
                                    phonePresent
                                )
                            }
                            ActionRow(
                                icon = Icons.Filled.Chat,
                                title = "Remind on WhatsApp",
                                subtitle = "Opens WhatsApp with the reminder ready to send"
                            ) {
                                val digits = whatsappDigits(phone)
                                open(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(reminderBody)}")),
                                    digits.isNotBlank()
                                )
                            }
                        }
                    }
                }
            }

            item(key = "history-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Payment history",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${payments.size} ${if (payments.size == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (payments.isEmpty()) {
                item(key = "history-empty") {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            Text("No payments recorded yet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(payments, key = { it.id }) { entry -> PaymentRow(entry) }
            }

            item(key = "spacer") { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ProgressCard(acc: Account) {
    val paidOff = acc.releaseApproved || acc.remainingBalance <= 0
    val progress = if (acc.totalLoanAmount > 0) {
        (acc.amountPaid.toFloat() / acc.totalLoanAmount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percent = if (acc.totalLoanAmount > 0) (progress * 100f).toInt() else 0
    val statusTint = when {
        paidOff -> SuccessGreen
        acc.status == AccountStatus.LOCKED -> MaterialTheme.colorScheme.error
        acc.status == AccountStatus.WARNING -> WarningAmber
        else -> MaterialTheme.colorScheme.primary
    }
    val statusLabel = when {
        paidOff -> "Paid off"
        acc.status == AccountStatus.LOCKED -> "Overdue"
        acc.status == AccountStatus.WARNING -> "Due soon"
        acc.status == AccountStatus.STOLEN -> "Stolen"
        else -> "Active"
    }
    val now = System.currentTimeMillis()
    val dueLabel = when {
        paidOff -> "Loan fully settled"
        acc.nextPaymentDueEpochMillis <= 0L -> "—"
        acc.nextPaymentDueEpochMillis < now -> "Overdue since ${formatDate(acc.nextPaymentDueEpochMillis)}"
        else -> "Next due ${formatDate(acc.nextPaymentDueEpochMillis)}"
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Repaid", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(acc.amountPaid), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier.size(48.dp).background(statusTint.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "$percent%", color = statusTint, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (paidOff) SuccessGreen else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("of ${formatAmount(acc.totalLoanAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(statusLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = statusTint)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Remaining ${formatAmount(acc.remainingBalance)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PaymentRow(entry: LedgerEntry) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDate(entry.dateEpochMillis),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatAmount(entry.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val method = entry.method.replace("_", " ").lowercase().replaceFirstChar { it.uppercaseChar() }
                val detail = if (entry.reference.isNotBlank()) "$method · ${entry.reference}" else method
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRelative(entry.dateEpochMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Builds a warm, encouraging payment reminder. Kept friendly and non-threatening
 * per the client's brief ("remind and encourage them to pay").
 */
private fun buildReminderText(acc: Account): String {
    val who = acc.customerName.takeIf { it.isNotBlank() } ?: "valued customer"
    val due = when {
        acc.remainingBalance <= 0 -> "Your loan is fully settled — thank you for being an excellent customer!"
        acc.nextPaymentDueEpochMillis <= 0L -> "your next instalment is due."
        acc.nextPaymentDueEpochMillis < System.currentTimeMillis() -> "your instalment of ${formatAmount(acc.dailyRate)} is now due. A quick payment today keeps your phone fully unlocked."
        else -> "your instalment of ${formatAmount(acc.dailyRate)} is due on ${formatDate(acc.nextPaymentDueEpochMillis)}. Staying on track keeps your phone fully unlocked."
    }
    return "Hello $who, this is Touch Base. A friendly reminder that $due You're doing great — every payment brings you closer to owning your device outright. Reply or call us if you need any help. Thank you for being a valued customer!"
}

/**
 * Ghana-friendly WhatsApp recipient digits: wa.me expects the international
 * number without a leading '+'. Stored numbers may begin with '0' (local) or
 * already carry the 233 country code.
 */
private fun whatsappDigits(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.startsWith("233") -> digits
        digits.startsWith("0") -> "233" + digits.drop(1)
        else -> digits
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
fun CustomerLedgerScreenPreview() {
    SecurePayAgentTheme {
        CustomerLedgerScreen(
            accountId = "a1",
            repository = null,
            onBack = {},
            onOpenAccount = {}
        )
    }
}
