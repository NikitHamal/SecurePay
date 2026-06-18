package com.touchbase.agent.ui.customers

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.touchbase.agent.data.model.Account
import com.touchbase.agent.data.model.AccountStatus
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import kotlinx.coroutines.launch

private val backgroundColor = Color(0xFF212121)
private val cardColor = Color(0xFF2A2A2A)
private val emerald = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    accountId: String,
    repository: SecurePayRepository?,
    onBack: () -> Unit,
    onProvisionDevice: (imei: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var account by remember { mutableStateOf<Account?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    val view = LocalView.current

    if (!isPreview) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
        }
    }

    fun loadAccount() {
        if (isPreview) return
        isLoading = true
        scope.launch {
            val result = repository?.getAccount(accountId) ?: run {
                isLoading = false
                return@launch
            }
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
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text(account?.customerName ?: "Account", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
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
                CircularProgressIndicator(color = emerald)
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
            Spacer(modifier = Modifier.height(0.dp))

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
                    onClick = { showPaymentSheet = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !actionInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = emerald,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(360.dp)
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
                                repository?.forceUnlock(acc.id)
                                actionInProgress = false
                                loadAccount()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !actionInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = emerald
                        ),
                        shape = RoundedCornerShape(360.dp)
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
                                repository?.forceLock(acc.id)
                                actionInProgress = false
                                loadAccount()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !actionInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(360.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Force Lock")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onProvisionDevice(acc.imei) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = emerald),
                shape = RoundedCornerShape(360.dp)
            ) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Re-provision / Generate QR")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPaymentSheet) {
        PaymentBottomSheet(
            accountId = accountId,
            repository = repository,
            onDismiss = { showPaymentSheet = false },
            onSuccess = {
                showPaymentSheet = false
                loadAccount()
            }
        )
    }
}

@Composable
private fun StatusBanner(status: AccountStatus) {
    val (text, color) = when (status) {
        AccountStatus.ACTIVE -> "Active" to emerald
        AccountStatus.WARNING -> "Warning — Payment Due Soon" to Color(0xFFFBBF24)
        AccountStatus.LOCKED -> "Locked — Payment Overdue" to Color(0xFFEF4444)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
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
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = emerald
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
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentBottomSheet(
    accountId: String,
    repository: SecurePayRepository?,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("MOBILE_MONEY") }
    var reference by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = backgroundColor,
        unfocusedContainerColor = backgroundColor,
        focusedBorderColor = emerald,
        unfocusedBorderColor = Color.Transparent,
        cursorColor = emerald
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cardColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Record Payment", style = MaterialTheme.typography.titleLarge, color = Color.White)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Amount (GHS)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input ->
                        val filtered = input.filter { c -> c.isDigit() || c == '.' }
                        if (filtered.count { it == '.' } <= 1 && filtered.substringAfter('.', "").length <= 2) amount = filtered
                    },
                    placeholder = { Text("Enter amount", color = Color.Gray.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = inputColors,
                    shape = RoundedCornerShape(360.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Payment Method", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MOBILE_MONEY", "CASH", "BANK").forEach { m ->
                        val isSelected = method == m
                        Button(
                            onClick = { method = m },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) emerald else backgroundColor,
                                contentColor = if (isSelected) Color.White else Color.Gray
                            ),
                            shape = RoundedCornerShape(360.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(if (m == "MOBILE_MONEY") "MOBILE MONEY" else m, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reference (optional)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    placeholder = { Text("Enter reference", color = Color.Gray.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = inputColors,
                    shape = RoundedCornerShape(360.dp)
                )
            }

            if (errorMessage != null) {
                Text(errorMessage!!, color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val amountCents = amount.toBigDecimalOrNull()?.movePointRight(2)?.toInt()
                    if (amountCents == null || amountCents <= 0) {
                        errorMessage = "Enter a valid amount"
                        return@Button
                    }
                    isSubmitting = true
                    scope.launch {
                        val result = repository?.recordPayment(
                            com.touchbase.agent.data.model.RecordPaymentRequest(
                                accountId = accountId,
                                amount = amountCents,
                                method = method,
                                reference = reference.ifBlank { null }
                            )
                        )
                        isSubmitting = false
                        result?.fold(
                            onSuccess = { onSuccess() },
                            onFailure = { errorMessage = it.message ?: "Payment failed" }
                        ) ?: onSuccess()
                    }
                },
                enabled = !isSubmitting && amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = emerald,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(360.dp)
            ) {
                if (isSubmitting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = Color.White)
                else Text("Record Payment")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CustomerDetailScreenPreview() {
    SecurePayAgentTheme {
        CustomerDetailScreen(
            accountId = "1",
            repository = null,
            onBack = {}
        )
    }
}
