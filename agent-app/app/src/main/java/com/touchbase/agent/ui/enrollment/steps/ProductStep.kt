package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.touchbase.agent.data.model.Device
import com.touchbase.agent.ui.components.BarcodeScannerSheet
import com.touchbase.agent.ui.enrollment.AgreementText
import com.touchbase.agent.ui.enrollment.DeviceLookupStatus
import com.touchbase.agent.ui.enrollment.EnrollmentStep
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M-KOPA "Product information": three wizard steps in one file.
 *  - PRODUCT: "Select the product serial you want to sell." (radio cards from stock)
 *  - OFFERS:  "Set the price for this sale" (dealer-entered total / term / daily rate)
 *  - LOAN:    "Loan details" summary card + editable initial payment
 */
@Composable
fun ProductStep(
    state: EnrollmentUiState,
    phase: EnrollmentStep,
    onSelectDevice: (Device) -> Unit,
    onRefreshDevices: () -> Unit,
    onDailyRateChange: (String) -> Unit,
    onTotalAmountChange: (String) -> Unit,
    onTermDaysChange: (String) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    onImeiChange: (String) -> Unit,
    onDeviceModelChange: (String) -> Unit,
    onEditSerial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (phase) {
            EnrollmentStep.PRODUCT -> SerialSelect(state, onSelectDevice, onRefreshDevices, onImeiChange, onDeviceModelChange)
            EnrollmentStep.OFFERS -> OfferSelect(state, onTotalAmountChange, onDailyRateChange, onTermDaysChange, onEditSerial)
            else -> LoanDetails(state, onDownPaymentChange)
        }
    }
}

// ---------------------------------------------------------------- product serial

@Composable
private fun SerialSelect(
    state: EnrollmentUiState,
    onSelectDevice: (Device) -> Unit,
    onRefreshDevices: () -> Unit,
    onImeiChange: (String) -> Unit,
    onDeviceModelChange: (String) -> Unit
) {
    var showScanner by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onRefreshDevices() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Select the product serial you want to sell.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (state.deviceLookupStatus is DeviceLookupStatus.AlreadySold) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF7F1D1D).copy(alpha = 0.45f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "This device is already sold. Pick another serial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFCA5A5)
                )
            }
        }

        if (state.isLoadingDevices) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Refreshing stock…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val groups = state.unsoldDevices.groupBy { it.model.ifBlank { "Other" } }
        groups.forEach { (model, devices) ->
            Text(
                model,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            devices.forEach { device ->
                SerialCard(
                    device = device,
                    selected = state.draft.imei == device.imei,
                    onClick = { onSelectDevice(device) }
                )
            }
        }

        if (!state.isLoadingDevices && groups.isEmpty()) {
            Text(
                "No unsold devices found in your stock.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Rescue paths — scan the box barcode, or type it in when offline.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { showScanner = true }) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan barcode")
            }
            TextButton(onClick = { showManual = true }) {
                Text("Enter manually")
            }
        }
    }

    if (showScanner) {
        BarcodeScannerSheet(
            onDismiss = { showScanner = false },
            onScan = { scanned ->
                onImeiChange(scanned)
                showScanner = false
            }
        )
    }

    if (showManual) {
        ManualSerialDialog(
            initialImei = state.draft.imei,
            initialModel = state.draft.deviceModel,
            onDismiss = { showManual = false },
            onUse = { imei, model ->
                onImeiChange(imei)
                onDeviceModelChange(model)
                showManual = false
            }
        )
    }
}

@Composable
private fun SerialCard(
    device: Device,
    selected: Boolean,
    onClick: () -> Unit
) {
    val date = remember(device.createdAt) {
        if (device.createdAt <= 0L) "" else {
            val millis = if (device.createdAt < 1_000_000_000_000L) device.createdAt * 1000 else device.createdAt
            runCatching { SimpleDateFormat("d/M/yy", Locale.UK).format(Date(millis)) }.getOrDefault("")
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    "${device.model} ~ ${device.imei.takeLast(4)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    device.imei,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (date.isNotBlank()) {
                    Text(
                        date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualSerialDialog(
    initialImei: String,
    initialModel: String,
    onDismiss: () -> Unit,
    onUse: (imei: String, model: String) -> Unit
) {
    var imei by remember { mutableStateOf(initialImei) }
    var model by remember { mutableStateOf(initialModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter device manually", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WizardTextField(
                    label = "IMEI (15 digits)",
                    value = imei,
                    onValueChange = { imei = it.filter { ch -> ch.isDigit() }.take(15) },
                    keyboardType = KeyboardType.Number
                )
                WizardTextField(
                    label = "Device model",
                    value = model,
                    onValueChange = { model = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUse(imei, model) },
                enabled = imei.length == 15 && model.isNotBlank()
            ) { Text("Use device") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------- offer (custom pricing)

/**
 * Pricing is 100% dealer-controlled: there are no preset packages any more, so the
 * agent types the exact price agreed with the customer. A suggested daily rate is
 * computed from (total − deposit) ÷ days but can always be overridden.
 */
@Composable
private fun OfferSelect(
    state: EnrollmentUiState,
    onTotalAmountChange: (String) -> Unit,
    onDailyRateChange: (String) -> Unit,
    onTermDaysChange: (String) -> Unit,
    onEditSerial: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Device IMEI",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                state.draft.imei,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = onEditSerial,
                shape = RoundedCornerShape(360.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Change product",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        val selectedDevice = state.availableDevices.firstOrNull { it.imei == state.draft.imei }
        val isLocked = selectedDevice?.totalAmount != null && selectedDevice?.dailyRate != null && selectedDevice?.termDays != null

        if (isLocked) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Admin-set price — cannot be changed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Total: ${AgreementText.money(selectedDevice?.totalAmount ?: 0)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Down: ${AgreementText.money(selectedDevice?.downPayment ?: 0)} · ${selectedDevice?.termDays ?: 0} days @ ${AgreementText.money(selectedDevice?.dailyRate ?: 0)}/day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Linked to ${selectedDevice?.productName ?: selectedDevice?.model ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text(
                "Set the price for this sale",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Your prices, your terms — type the amounts you agreed with this customer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    WizardTextField(
                        label = "Total price (GH\u20B5)",
                        value = state.totalAmountInput,
                        onValueChange = onTotalAmountChange,
                        keyboardType = KeyboardType.Decimal,
                        isError = state.totalAmountInput.isNotEmpty() && !state.isTotalAmountValid,
                        supportingText = "Full amount the customer repays, e.g. 2277.80"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WizardTextField(
                        label = "Repayment period (days)",
                        value = state.termDaysInput,
                        onValueChange = onTermDaysChange,
                        keyboardType = KeyboardType.Number,
                        isError = state.termDaysInput.isNotEmpty() && !state.isTermDaysValid,
                        supportingText = "Whole days, e.g. 119"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WizardTextField(
                        label = "Daily repayment rate (GH\u20B5)",
                        value = state.dailyRateInput,
                        onValueChange = onDailyRateChange,
                        keyboardType = KeyboardType.Decimal,
                        isError = state.dailyRateInput.isNotEmpty() && !state.isDailyRateValid,
                        supportingText = "Amount due each day, e.g. 16.20"
                    )

                    val suggested = state.suggestedDailyRateCents
                    if (suggested > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Suggested ${AgreementText.money(suggested)} / day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                onDailyRateChange(String.format(Locale.UK, "%.2f", suggested / 100.0))
                            }) { Text("Use") }
                        }
                    }
                }
            }
        }

        if (state.isOffersStepValid) {
            OfferSummaryCard(
                total = AgreementText.money((state.totalAmountInput.toDoubleOrNull() ?: 0.0).let { (it * 100).toInt() }),
                dailyRate = AgreementText.money((state.dailyRateInput.toDoubleOrNull() ?: 0.0).let { (it * 100).toInt() }),
                termDays = state.termDaysInput.toIntOrNull() ?: 0
            )
        }
    }
}

/** M-KOPA style offer card, rendered live from the dealer's own numbers. */
@Composable
private fun OfferSummaryCard(
    total: String,
    dailyRate: String,
    termDays: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Your offer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LockedAmountRow("Daily repayment rate $dailyRate")
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(360.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "$termDays DAYS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Total $total",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun LockedAmountRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- loan details

@Composable
private fun LoanDetails(
    state: EnrollmentUiState,
    onDownPaymentChange: (String) -> Unit
) {
    val draft = state.draft
    val planName = draft.deviceModel.ifBlank { "Your offer" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // DEVICE IMEI card (M-KOPA stacked grey card)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "DEVICE IMEI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    draft.imei,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Text(
            "Loan details",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // M-KOPA green card → Touch Base gold card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        planName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                GoldCardRow("Initial payment ${AgreementText.money(draft.downPayment)}")
                GoldCardRow("Daily repayment rate ${AgreementText.money(draft.dailyRate)}")
                GoldCardRow("Repayment Period ${draft.termDays} Days")
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "Total loan amount ${AgreementText.money(draft.totalLoanAmount)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        WizardTextField(
            label = "Initial payment (deposit)",
            value = state.downPaymentInput,
            onValueChange = onDownPaymentChange,
            keyboardType = KeyboardType.Decimal,
            isError = state.downPaymentInput.isNotEmpty() && !state.isDownPaymentValid,
            supportingText = "Deposit paid today \u2014 any amount from 0 up to the total price"
        )
    }
}

@Composable
private fun GoldCardRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
