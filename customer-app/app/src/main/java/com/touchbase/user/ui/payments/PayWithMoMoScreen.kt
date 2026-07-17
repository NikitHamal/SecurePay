package com.touchbase.user.ui.payments

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.MomoProvider
import com.touchbase.user.data.model.VerifyPaystackResponse
import com.touchbase.user.data.model.formatCentsAsCurrency
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.ui.theme.Amber
import com.touchbase.user.ui.theme.Charcoal
import com.touchbase.user.ui.theme.CharcoalElevated
import com.touchbase.user.ui.theme.CharcoalSurfaceVariant
import com.touchbase.user.ui.theme.Crimson
import com.touchbase.user.ui.theme.Emerald
import com.touchbase.user.ui.theme.TextPrimary
import com.touchbase.user.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MomoStep { FORM, PROCESSING, OTP, SUCCESS, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayWithMoMoScreen(
    repository: DeviceRepository,
    account: LoanAccount?,
    onBack: () -> Unit,
    onPaid: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dailyRate = account?.dailyRateCents ?: 0
    val remaining = account?.remainingBalanceCents ?: 0

    var step by remember { mutableStateOf(MomoStep.FORM) }
    var provider by remember { mutableStateOf(MomoProvider.MTN) }
    var phone by remember { mutableStateOf("") }
    var amountText by remember {
        mutableStateOf(if (dailyRate > 0) "%.2f".format(dailyRate / 100.0) else "")
    }
    var otp by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reference by remember { mutableStateOf("") }
    var displayText by remember { mutableStateOf("") }
    var verifyResult by remember { mutableStateOf<VerifyPaystackResponse?>(null) }
    var polling by remember { mutableStateOf(false) }

    val otpFocus = remember { FocusRequester() }

    Scaffold(
        containerColor = Charcoal,
        topBar = {
            TopAppBar(
                title = { Text("Pay with Mobile Money", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Charcoal)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (step) {
                MomoStep.FORM -> PaymentForm(
                    account = account,
                    amountText = amountText,
                    onAmountChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                    phone = phone,
                    onPhoneChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(13) },
                    provider = provider,
                    onProviderChange = { provider = it },
                    remaining = remaining,
                    dailyRate = dailyRate,
                    error = error,
                    onPayDaily = {
                        if (dailyRate > 0) amountText = "%.2f".format(dailyRate / 100.0)
                    },
                    onPayFull = {
                        if (remaining > 0) amountText = "%.2f".format(remaining / 100.0)
                    },
                    onSubmit = {
                        error = null
                        val amt = amountText.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            error = "Enter a valid amount"
                            return@PaymentForm
                        }
                        if (!phone.matches(Regex("^0?[25]\\d{8}$")) && !phone.matches(Regex("^\\+233\\d{9}$"))) {
                            error = "Enter a valid Ghana phone number (e.g. 055xxxxxxx)"
                            return@PaymentForm
                        }
                        step = MomoStep.PROCESSING
                        scope.launch {
                            val result = repository.paystackInitialize(amt, phone, provider.key)
                            result.onSuccess { resp ->
                                reference = resp.reference
                                displayText = resp.displayText
                                if (resp.otpRequired) {
                                    step = MomoStep.OTP
                                } else {
                                    polling = true
                                    startPolling(repository, reference,
                                        onUpdate = {
                                            verifyResult = it
                                            if (it.status == "success") {
                                                polling = false
                                                step = MomoStep.SUCCESS
                                            } else if (it.status == "failed" || it.status == "abandoned" || it.status == "expired") {
                                                polling = false
                                                step = MomoStep.FAILED
                                                error = "Payment was not completed."
                                            }
                                        },
                                        onTimeout = {
                                            polling = false
                                            step = MomoStep.FAILED
                                            error = "We didn't receive confirmation. Check your MoMo wallet and try again."
                                        }
                                    )
                                }
                            }.onFailure {
                                error = it.message ?: "Failed to start payment"
                                step = MomoStep.FORM
                            }
                        }
                    }
                )

                MomoStep.PROCESSING -> ProcessingCard(displayText)

                MomoStep.OTP -> {
                    LaunchedEffect(Unit) { runCatching { otpFocus.requestFocus() } }
                    OtpCard(
                        reference = reference,
                        provider = provider,
                        otp = otp,
                        onOtpChange = { otp = it.filter { c -> c.isDigit() }.take(6) },
                        error = error,
                        submitting = polling,
                        focusRequester = otpFocus,
                        onSubmit = {
                            error = null
                            if (otp.length < 4) {
                                error = "Enter the OTP sent to your phone"
                                return@OtpCard
                            }
                            polling = true
                            scope.launch {
                                val r = repository.paystackSubmitOtp(reference, otp)
                                r.onSuccess { resp ->
                                    when {
                                        resp.status == "success" -> {
                                            val v = repository.paystackVerify(reference).getOrNull()
                                            verifyResult = v
                                            polling = false
                                            step = if (v?.status == "success") MomoStep.SUCCESS else MomoStep.FAILED
                                        }
                                        resp.otpRequired -> {
                                            polling = false
                                            error = resp.message ?: "Incorrect OTP. Try again."
                                        }
                                        else -> {
                                            // keep polling for async confirmation
                                            startPolling(repository, reference,
                                                onUpdate = {
                                                    verifyResult = it
                                                    if (it.status == "success") {
                                                        polling = false; step = MomoStep.SUCCESS
                                                    } else if (it.status in listOf("failed", "abandoned", "expired")) {
                                                        polling = false; step = MomoStep.FAILED
                                                        error = "Payment was not completed."
                                                    }
                                                },
                                                onTimeout = {
                                                    polling = false; step = MomoStep.FAILED
                                                    error = "Confirmation timed out."
                                                }
                                            )
                                        }
                                    }
                                }.onFailure {
                                    polling = false
                                    error = it.message ?: "OTP submission failed"
                                }
                            }
                        },
                        onCancel = { step = MomoStep.FORM; error = null; otp = "" }
                    )
                }

                MomoStep.SUCCESS -> {
                    SuccessCard(verifyResult, account)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onPaid() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color(0xFF07130F))
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }

                MomoStep.FAILED -> {
                    FailedCard(error)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { step = MomoStep.FORM; error = null; otp = ""; reference = "" },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) { Text("Try again", fontWeight = FontWeight.SemiBold) }
                        Button(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CharcoalSurfaceVariant, contentColor = TextPrimary)
                        ) { Text("Back") }
                    }
                }
            }
        }
    }
}

private suspend fun startPolling(
    repository: DeviceRepository,
    reference: String,
    onUpdate: (VerifyPaystackResponse) -> Unit,
    onTimeout: () -> Unit
) {
    val start = System.currentTimeMillis()
    val timeout = 120_000L
    while (System.currentTimeMillis() - start < timeout) {
        delay(3_000)
        val r = repository.paystackVerify(reference).getOrNull() ?: continue
        onUpdate(r)
        if (r.status == "success" || r.status in listOf("failed", "abandoned", "expired")) return
    }
    onTimeout()
}

@Composable
private fun PaymentForm(
    account: LoanAccount?,
    amountText: String,
    onAmountChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    provider: MomoProvider,
    onProviderChange: (MomoProvider) -> Unit,
    remaining: Int,
    dailyRate: Int,
    error: String?,
    onPayDaily: () -> Unit,
    onPayFull: () -> Unit,
    onSubmit: () -> Unit
) {
    val appTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Emerald,
        unfocusedBorderColor = CharcoalSurfaceVariant,
        cursorColor = Emerald,
        focusedLabelColor = Emerald,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        backgroundColor = CharcoalElevated
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Amount due", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(
                text = formatCentsAsCurrency(remaining, account?.currencyCode ?: "GHS"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dailyRate > 0) {
                    FilterChip(
                        selected = false,
                        onClick = onPayDaily,
                        label = { Text("Pay 1 day") },
                        colors = chipColors()
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = onPayFull,
                    label = { Text("Pay full balance") },
                    colors = chipColors()
                )
            }
        }
    }

    OutlinedTextField(
        value = amountText,
        onValueChange = onAmountChange,
        label = { Text("Amount (GHS)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = appTextFieldColors,
        modifier = Modifier.fillMaxWidth()
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Network", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MomoProvider.entries.forEach { p ->
                FilterChip(
                    selected = p == provider,
                    onClick = { onProviderChange(p) },
                    label = { Text(p.display, fontWeight = FontWeight.SemiBold) },
                    colors = chipColors(selected = p == provider),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        label = { Text("Mobile money number") },
        placeholder = { Text("055xxxxxxx") },
        leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = TextSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = appTextFieldColors,
        modifier = Modifier.fillMaxWidth()
    )

    error?.let {
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Crimson.copy(alpha = 0.14f))) {
            Text(it, color = Crimson, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }

    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color(0xFF07130F))
    ) {
        Text("Pay now", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProcessingCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            CircularProgressIndicator(color = Emerald, strokeWidth = 3.dp)
            Text("Processing…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                text.ifBlank { "Waiting for confirmation from your mobile money provider." },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtpCard(
    reference: String,
    provider: MomoProvider,
    otp: String,
    onOtpChange: (String) -> Unit,
    error: String?,
    submitting: Boolean,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val appTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Emerald,
        unfocusedBorderColor = CharcoalSurfaceVariant,
        cursorColor = Emerald,
        focusedLabelColor = Emerald,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        backgroundColor = CharcoalElevated
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Enter OTP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "A one-time PIN was sent to your ${provider.display} number. Enter it below to complete the payment.",
                color = TextSecondary, style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = otp,
                onValueChange = onOtpChange,
                label = { Text("OTP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = appTextFieldColors,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            error?.let {
                Text(it, color = Crimson, style = MaterialTheme.typography.bodySmall)
            }
            if (reference.isNotBlank()) {
                Text("Ref: $reference", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onSubmit,
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color(0xFF07130F))
            ) {
                if (submitting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF07130F))
                else Text("Submit OTP", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun SuccessCard(result: VerifyPaystackResponse?, account: LoanAccount?) {
    val amount = result?.amount ?: 0
    val currency = result?.currency ?: account?.currencyCode ?: "GHS"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Emerald.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(64.dp).background(Emerald, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF07130F), modifier = Modifier.size(36.dp))
            }
            Text("Payment successful", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(formatCentsAsCurrency(amount, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Emerald)
            if (result?.paidOff == true) {
                Text("Your loan is fully paid. Your device will unlock shortly.", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Your next due date has been extended. Device will sync and unlock if locked.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun FailedCard(error: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Crimson.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(64.dp).background(Crimson, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Error, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Text("Payment not completed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                error ?: "We couldn't confirm your payment. No charge was made.",
                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun chipColors(selected: Boolean = false) = FilterChipDefaults.filterChipColors(
    containerColor = if (selected) Emerald else CharcoalSurfaceVariant,
    labelColor = if (selected) Color(0xFF07130F) else TextPrimary,
    selectedContainerColor = Emerald,
    selectedLabelColor = Color(0xFF07130F),
    iconColor = if (selected) Color(0xFF07130F) else TextSecondary
)


