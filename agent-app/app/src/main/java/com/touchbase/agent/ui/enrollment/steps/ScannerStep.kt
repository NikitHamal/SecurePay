package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.agent.ui.components.BarcodeScannerSheet
import com.touchbase.agent.ui.enrollment.DeviceLookupStatus
import com.touchbase.agent.ui.enrollment.EnrollmentDraft
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.res.stringResource
import com.touchbase.agent.R

@Composable
fun ScannerStep(
    state: EnrollmentUiState,
    onImeiChange: (String) -> Unit,
    onDeviceModelChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showScanner by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_imei), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.draft.imei,
                onValueChange = onImeiChange,
                placeholder = { Text("Scan or enter IMEI", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.draft.imei.isNotEmpty() && !state.isImeiValid,
                supportingText = {
                    Text("${state.draft.imei.length}/15 digits", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(360.dp),
                trailingIcon = {
                    Icon(
                        Icons.Filled.QrCode2,
                        contentDescription = "Scan IMEI",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        Button(
            onClick = { showScanner = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(360.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan IMEI Barcode", style = MaterialTheme.typography.labelLarge)
        }

        DeviceLookupChip(lookupStatus = state.deviceLookupStatus)

        val isModelFromInventory = state.deviceLookupStatus is DeviceLookupStatus.Found
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_device_model), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.draft.deviceModel,
                onValueChange = onDeviceModelChange,
                placeholder = { Text("Enter device model", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                enabled = !isModelFromInventory,
                isError = state.draft.deviceModel.isNotEmpty() && !state.isDeviceModelValid,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    disabledTextColor = MaterialTheme.colorScheme.onBackground,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(360.dp)
            )
            if (isModelFromInventory) {
                Text("Auto-filled from inventory", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
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
private fun DeviceLookupChip(lookupStatus: DeviceLookupStatus) {
    when (lookupStatus) {
        is DeviceLookupStatus.Found -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${lookupStatus.model} (in stock)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
        is DeviceLookupStatus.AlreadySold -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Device already enrolled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            }
        }
        is DeviceLookupStatus.NotFound -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Not in inventory \u2014 enter model manually", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Medium)
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
            onDeviceModelChange = {}
        )
    }
}
