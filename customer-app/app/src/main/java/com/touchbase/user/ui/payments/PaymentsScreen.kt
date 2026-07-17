package com.touchbase.user.ui.payments

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.PaymentEntry
import com.touchbase.user.data.model.formatCentsAsCurrency
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.ui.theme.Charcoal
import com.touchbase.user.ui.theme.CharcoalElevated
import com.touchbase.user.ui.theme.Emerald
import com.touchbase.user.ui.theme.TextPrimary
import com.touchbase.user.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    repository: DeviceRepository,
    onBack: () -> Unit,
    onPayNow: () -> Unit
) {
    val account by repository.account.collectAsState()
    val payments by repository.payments.collectAsState()

    LaunchedEffect(Unit) {
        repository.refreshPayments()
    }

    Scaffold(
        containerColor = Charcoal,
        topBar = {
            TopAppBar(
                title = { Text("Payment History", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Charcoal,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        if (payments.isEmpty() && account == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Emerald)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                account?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Remaining balance",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                formatCentsAsCurrency(it.remainingBalanceCents, it.currencyCode),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = onPayNow,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald,
                                    contentColor = Color(0xFF07130F)
                                )
                            ) {
                                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pay with Mobile Money", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (payments.isEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Receipt,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No payments yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    payments.forEach { payment ->
                        PaymentCard(payment, account)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PaymentCard(payment: PaymentEntry, account: LoanAccount?) {
    val currencyCode = account?.currencyCode ?: "GHS"
    val date = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(payment.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = payment.method.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (!payment.reference.isNullOrBlank()) {
                    Text(
                        text = "Ref: ${payment.reference}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Text(
                text = formatCentsAsCurrency(payment.amount, currencyCode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Emerald
            )
        }
    }
}
