package com.securepay.agent.ui.customers

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securepay.agent.data.model.Account
import com.securepay.agent.data.model.AccountStatus
import com.securepay.agent.data.model.formatAmount
import com.securepay.agent.data.remote.SecurePayRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    accountId: String,
    repository: SecurePayRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var account by remember { mutableStateOf<Account?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadAccount() {
        isLoading = true
        scope.launch {
            val result = repository.getAccount(accountId)
            isLoading = false
            result.fold(
                onSuccess = { account = it },
                onFailure = { error = it.message }
            )
        }
    }

    LaunchedEffect(accountId) { loadAccount() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(account?.customerName ?: "Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val acc = account
        if (acc == null || error != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error ?: "Account not found", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusBanner(status = acc.status)

            InfoCard(title = "Customer Information") {
                InfoRow(icon = Icons.Filled.Person, label = "Name", value = acc.customerName)
                InfoRow(icon = Icons.Filled.Phone, label = "Phone", value = acc.phoneNumber)
                InfoRow(icon = Icons.Filled.Lock, label = "National ID", value = acc.nationalId)
            }

            InfoCard(title = "Device & Plan") {
                InfoRow(icon = null, label = "IMEI", value = acc.imei)
                InfoRow(icon = null, label = "Model", value = acc.deviceModel)
                InfoRow(icon = null, label = "Plan", value = acc.planName)
                InfoRow(icon = null, label = "Term", value = "${acc.termDays} days")
                InfoRow(icon = null, label = "Daily Rate", value = formatAmount(acc.dailyRate))
            }

            InfoCard(title = "Financial Summary") {
                InfoRow(icon = null, label = "Total Loan", value = formatAmount(acc.totalLoanAmount))
                InfoRow(icon = null, label = "Amount Paid", value = formatAmount(acc.amountPaid))
                InfoRow(icon = null, label = "Remaining", value = formatAmount(acc.remainingBalance))
                InfoRow(icon = null, label = "Down Payment", value = formatAmount(acc.downPayment))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showPaymentDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = !actionInProgress
                ) {
                    Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Record Payment")
                }

                if (acc.status == AccountStatus.LOCKED) {
                    OutlinedButton(
                        onClick = {
                            actionInProgress = true
                            scope.launch {
                                repository.forceUnlock(acc.id)
                                actionInProgress = false
                                loadAccount()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !actionInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            actionInProgress = true
                            scope.launch {
                                repository.forceLock(acc.id)
                                actionInProgress = false
                                loadAccount()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !actionInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Force Lock")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPaymentDialog) {
        PaymentDialog(
            accountId = accountId,
            repository = repository,
            onDismiss = { showPaymentDialog = false },
            onSuccess = {
                showPaymentDialog = false
                loadAccount()
            }
        )
    }
}

@Composable
private fun StatusBanner(status: AccountStatus) {
    val (text, color) = when (status) {
        AccountStatus.ACTIVE -> "Active" to MaterialTheme.colorScheme.primary
        AccountStatus.WARNING -> "Warning — Payment Due Soon" to MaterialTheme.colorScheme.tertiary
        AccountStatus.LOCKED -> "Locked — Payment Overdue" to MaterialTheme.colorScheme.error
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentDialog(
    accountId: String,
    repository: SecurePayRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("M-PESA") }
    var reference by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() } },
                    label = { Text("Amount (KES)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("M-PESA", "CASH", "BANK").forEach { m ->
                        FilterChipPayment(
                            selected = method == m,
                            onClick = { method = m },
                            label = m
                        )
                    }
                }

                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Reference (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountCents = amount.toIntOrNull()
                    if (amountCents == null || amountCents <= 0) {
                        errorMessage = "Enter a valid amount"
                        return@Button
                    }
                    isSubmitting = true
                    scope.launch {
                        val result = repository.recordPayment(
                            com.securepay.agent.data.model.RecordPaymentRequest(
                                accountId = accountId,
                                amount = amountCents,
                                method = method,
                                reference = reference.ifBlank { null }
                            )
                        )
                        isSubmitting = false
                        result.fold(
                            onSuccess = { onSuccess() },
                            onFailure = { errorMessage = it.message ?: "Payment failed" }
                        )
                    }
                },
                enabled = !isSubmitting && amount.isNotBlank()
            ) {
                if (isSubmitting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else Text("Record")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FilterChipPayment(selected: Boolean, onClick: () -> Unit, label: String) {
    if (selected) {
        Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}