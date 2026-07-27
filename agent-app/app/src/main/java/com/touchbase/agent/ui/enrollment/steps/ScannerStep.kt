package com.touchbase.agent.ui.enrollment.steps

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.agent.R
import com.touchbase.agent.data.model.Device
import com.touchbase.agent.ui.components.BarcodeScannerSheet
import com.touchbase.agent.ui.components.DevicePickerDialog
import com.touchbase.agent.ui.components.DevicePickerFilter
import com.touchbase.agent.ui.enrollment.DeviceLookupStatus
import com.touchbase.agent.ui.enrollment.EnrollmentDraft
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import com.touchbase.agent.ui.theme.SecurePayAgentTheme

/**
 * Device step — selection-first.
 *
 * Agents should never type an IMEI or a model name during a sale: they pick
 * the physical device from the live "not yet sold" inventory list. Scanning a
 * barcode stays as the fast path when the box is in hand, and manual entry is
 * kept as a clearly-marked rescue path only (offline / stock not added yet).
 */
@Composable
fun ScannerStep(
    state: EnrollmentUiState,
    onImeiChange: (String) -> Unit,
    onDeviceModelChange: (String) -> Unit,
    onSelectDevice: (Device) -> Unit,
    onRefreshDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var manualMode by remember { mutableStateOf(false) }

    // Always work with fresh stock data when the agent lands on this step.
    LaunchedEffect(Unit) { onRefreshDevices() }

    val lookup = state.deviceLookupStatus
    val hasSelection = state.isImeiValid &&
        state.draft.deviceModel.isNotBlank() &&
        lookup !is DeviceLookupStatus.AlreadySold &&
        !manualMode

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (hasSelection) {
            SelectedDeviceCard(
                model = state.draft.deviceModel,
                imei = state.draft.imei,
                inStock = lookup is DeviceLookupStatus.Found,
                onChange = { showPicker = true }
            )
        } else {
            Text(
                "Which device is the customer buying? Pick it from your stock — no typing needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedCard(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_inventory),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Choose from inventory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            when {
                                state.isLoadingDevices -> "Refreshing stock…"
                                else -> "${state.unsoldDevices.size} unsold device(s) available"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.draft.imei.isNotBlank()) {
                Text(
                    "Selected IMEI: ${state.draft.imei}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { showScanner = true }) {
                    Icon(
                        Icons.Filled.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan barcode")
                }
                TextButton(onClick = { manualMode = true }) {
                    Text("Enter manually")
                }
            }
        }

        DeviceLookupChip(lookupStatus = lookup)

        // Manual / not-in-stock rescue path. Model stays editable only when the
        // IMEI was not matched to a stock device.
        if (manualMode || lookup is DeviceLookupStatus.NotFound) {
            ManualEntrySection(
                state = state,
                onImeiChange = onImeiChange,
                onDeviceModelChange = onDeviceModelChange,
                onClose = if (manualMode) ({ manualMode = false }) else null
            )
        }
    }

    if (showPicker) {
        DevicePickerDialog(
            devices = state.availableDevices,
            filter = DevicePickerFilter.IN_STOCK,
            isLoading = state.isLoadingDevices,
            onRefresh = onRefreshDevices,
            onSelect = { device ->
                onSelectDevice(device)
                manualMode = false
                showPicker = false
            },
            onDismiss = { showPicker = false },
            onScanRequested = {
                showPicker = false
                showScanner = true
            },
            onManualRequested = {
                showPicker = false
                manualMode = true
            }
        )
    }

    if (showScanner) {
        BarcodeScannerSheet(
            onDismiss = { showScanner = false },
            title = "Scan Device IMEI",
            subtitle = "Point camera at the IMEI barcode on the box or sticker.",
            onScan = { scanned ->
                onImeiChange(scanned)
                showScanner = false
            }
        )
    }
}

@Composable
private fun SelectedDeviceCard(
    model: String,
    imei: String,
    inStock: Boolean,
    onChange: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF10B981).copy(alpha = 0.10f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    imei,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (inStock) "In stock — ready to sell" else "Not matched to stock",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inStock) Color(0xFF059669) else MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
    }
}

@Composable
private fun ManualEntrySection(
    state: EnrollmentUiState,
    onImeiChange: (String) -> Unit,
    onDeviceModelChange: (String) -> Unit,
    onClose: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onClose != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Manual entry (rescue)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClose) { Text("Hide") }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_imei), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.draft.imei,
                onValueChange = onImeiChange,
                placeholder = { Text("Enter 15-digit IMEI", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.draft.imei.isNotEmpty() && !state.isImeiValid,
                supportingText = {
                    Text("${state.draft.imei.length}/15 digits", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }

        if (state.deviceLookupStatus !is DeviceLookupStatus.Found) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.label_device_model), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.draft.deviceModel,
                    onValueChange = onDeviceModelChange,
                    placeholder = { Text("Enter device model", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine = true,
                    isError = state.draft.deviceModel.isNotEmpty() && !state.isDeviceModelValid,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(360.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceLookupChip(lookupStatus: DeviceLookupStatus) {
    when (lookupStatus) {
        is DeviceLookupStatus.Found -> { /* covered by SelectedDeviceCard */ }
        is DeviceLookupStatus.AlreadySold -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("This device is already enrolled to another account. Pick a different one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            }
        }
        is DeviceLookupStatus.NotFound -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Not in inventory — enter model manually", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Medium)
                }
            }
        }
        DeviceLookupStatus.Idle -> {}
    }
}

@ComposePreview(showBackground = true)
@Composable
fun ScannerStepPreview() {
    SecurePayAgentTheme {
        ScannerStep(
            state = EnrollmentUiState(draft = EnrollmentDraft(imei = "123456789012345", deviceModel = "Solar X1")),
            onImeiChange = {},
            onDeviceModelChange = {},
            onSelectDevice = {},
            onRefreshDevices = {}
        )
    }
}
