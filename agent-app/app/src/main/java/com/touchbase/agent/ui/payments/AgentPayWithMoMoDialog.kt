package com.touchbase.agent.ui.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.touchbase.agent.data.model.Account
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.theme.Gold
import com.touchbase.agent.ui.theme.VividCrimson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MomoStep { FORM, PROCESSING, OTP, SUCCESS, FAILED }

private enum class MomoProvider(val key: String, val display: String) {
    MTN("mtn", "MTN"), TELECEL("vod", "Telecel"), AIRTELTIGO("tgo", "AirtelTigo");
    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: MTN
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPayWithMoMoDialog(
    repository: SecurePayRepository,
    account: Account,
    onDismiss: () -> Unit,
    onPaymentRecorded: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    var step by remember { mutableStateOf(MomoStep.FORM) }
    var provider by remember { mutableStateOf(MomoProvider.MTN) }
    var phone by remember { mutableStateOf(account.phoneNumber) }
    var amountText by remember { mutableStateOf("%.2f".format(account.dailyRate / 100.0)) }
    var otp by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reference by remember { mutableStateOf("") }
    var displayText by remember { mutableStateOf("") }
    val otpFocus = remember { FocusRequester() }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Gold,
        unfocusedBorderColor = outline,
        cursorColor = Gold,
        focusedLabelColor = Gold,
        unfocusedLabelColor = onSurfaceVariant,
        focusedTextColor = onSurface,
        unfocusedTextColor = onSurface,
        focusedContainerColor = surfaceVariant,
        unfocusedContainerColor = surfaceVariant
    )

    fun pollVerification(ref: String) {
        scope.launch {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 120_000L) {
                delay(3_000)
                val r = repository.paystackVerify(ref).getOrNull() ?: continue
                if (r.status == "success") { step = MomoStep.SUCCESS; return@launch }
                if (r.status in listOf("failed", "abandoned", "expired")) {
                    step = MomoStep.FAILED; error = "Payment was not completed."; return@launch
                }
            }
            step = MomoStep.FAILED; error = "Confirmation timed out."
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: title + close X
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            "Pay with MoMo",
                            color = onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            account.customerName,
                            color = onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = onSurfaceVariant)
                    }
                }

                when (step) {
                    MomoStep.FORM -> {
                        // Balance card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Remaining balance",
                                    color = onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    formatAmount(account.remainingBalance),
                                    color = onSurface,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QuickChip(
                                        label = "1 day",
                                        selected = amountText == "%.2f".format(account.dailyRate / 100.0),
                                        onClick = { amountText = "%.2f".format(account.dailyRate / 100.0) }
                                    )
                                    QuickChip(
                                        label = "Full",
                                        selected = amountText == "%.2f".format(account.remainingBalance / 100.0),
                                        onClick = { amountText = "%.2f".format(account.remainingBalance / 100.0) }
                                    )
                                }
                            }
                        }

                        // Amount
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                            label = { Text("Amount (GHS)") },
                            prefix = { Text("GH\u20B5 ", fontWeight = FontWeight.SemiBold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Network
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Network",
                                color = onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MomoProvider.entries.forEach { p ->
                                    ProviderChip(
                                        label = p.display,
                                        selected = p == provider,
                                        onClick = { provider = p },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Phone
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(13) },
                            label = { Text("Customer MoMo number") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Error
                        error?.let {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = VividCrimson.copy(alpha = 0.12f)
                                )
                            ) {
                                Text(
                                    it,
                                    color = VividCrimson,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Primary CTA
                        Button(
                            onClick = {
                                error = null
                                val amt = amountText.toDoubleOrNull()
                                if (amt == null || amt <= 0) {
                                    error = "Enter a valid amount"; return@Button
                                }
                                if (!phone.matches(Regex("^0?[25]\\d{8}$")) &&
                                    !phone.matches(Regex("^\\+233\\d{9}$"))
                                ) {
                                    error = "Enter a valid Ghana number (055xxxxxxx)"; return@Button
                                }
                                step = MomoStep.PROCESSING
                                scope.launch {
                                    repository.paystackInitialize(
                                        account.id, amt, phone, provider.key
                                    ).onSuccess { resp ->
                                        reference = resp.reference
                                        displayText = resp.displayText ?: ""
                                        if (resp.otpRequired) {
                                            step = MomoStep.OTP
                                        } else pollVerification(resp.reference)
                                    }.onFailure {
                                        error = it.message
                                        step = MomoStep.FORM
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = Color(0xFF0B0B0C)
                            )
                        ) { Text("Charge MoMo", fontWeight = FontWeight.Bold) }
                    }

                    MomoStep.PROCESSING -> ProcessingBlock(
                        displayText, surfaceVariant, onSurface, onSurfaceVariant
                    )

                    MomoStep.OTP -> {
                        LaunchedEffect(Unit) { runCatching { otpFocus.requestFocus() } }
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Enter OTP from customer",
                                    color = onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Ask the customer for the OTP sent to ${provider.display}.",
                                    color = onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = otp,
                                    onValueChange = {
                                        otp = it.filter { c -> c.isDigit() }.take(6)
                                    },
                                    label = { Text("OTP") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth().focusRequester(otpFocus)
                                )
                                error?.let {
                                    Text(
                                        it,
                                        color = VividCrimson,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = {
                                        error = null
                                        if (otp.length < 4) {
                                            error = "Enter the OTP"; return@Button
                                        }
                                        scope.launch {
                                            repository.paystackSubmitOtp(reference, otp).onSuccess { r ->
                                                when {
                                                    r.status == "success" -> step = MomoStep.SUCCESS
                                                    r.otpRequired ->
                                                        error = r.message ?: "Incorrect OTP"
                                                    else -> pollVerification(reference)
                                                }
                                            }.onFailure { error = it.message }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Gold,
                                        contentColor = Color(0xFF0B0B0C)
                                    )
                                ) { Text("Submit OTP", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = {
                                        step = MomoStep.FORM; otp = ""; error = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("Cancel") }
                            }
                        }
                    }

                    MomoStep.SUCCESS -> {
                        ResultBlock(
                            success = true,
                            title = "Payment successful",
                            subtitle = "Recorded via MoMo. Device will unlock on next sync.",
                            surfaceVariant = surfaceVariant,
                            onSurface = onSurface,
                            onSurfaceVariant = onSurfaceVariant
                        )
                        Button(
                            onClick = onPaymentRecorded,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = Color(0xFF0B0B0C)
                            )
                        ) { Text("Done", fontWeight = FontWeight.Bold) }
                    }

                    MomoStep.FAILED -> {
                        ResultBlock(
                            success = false,
                            title = "Payment not completed",
                            subtitle = error ?: "No charge was made.",
                            surfaceVariant = surfaceVariant,
                            onSurface = onSurface,
                            onSurfaceVariant = onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    step = MomoStep.FORM; otp = ""; error = null
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Try again") }
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = surfaceVariant,
                                    contentColor = onSurface
                                )
                            ) { Text("Close") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingBlock(
    text: String, surfaceVariant: Color, onSurface: Color, onSurfaceVariant: Color
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
            Text(
                "Waiting for MoMo…",
                color = onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text.ifBlank { "Customer must approve the prompt on their phone." },
                color = onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ResultBlock(
    success: Boolean,
    title: String,
    subtitle: String,
    surfaceVariant: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    val color = if (success) Gold else VividCrimson
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(color, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (success) Color(0xFF0B0B0C) else Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                title,
                color = onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                subtitle,
                color = onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = Gold,
            selectedLabelColor = Color(0xFF0B0B0C),
            selectedLeadingIconColor = Color(0xFF0B0B0C)
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            selectedBorderColor = Gold,
            enabled = true,
            selected = selected
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = Gold,
            selectedLabelColor = Color(0xFF0B0B0C)
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            selectedBorderColor = Gold,
            enabled = true,
            selected = selected
        )
    )
}
