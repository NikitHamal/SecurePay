package com.touchbase.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.touchbase.agent.R
import com.touchbase.agent.data.model.Device

/** Which slice of the dealer inventory the picker presents. */
enum class DevicePickerFilter {
    /** Not sold yet — eligible for a new enrollment. */
    IN_STOCK,

    /** Already enrolled — eligible for (re)provisioning. */
    SOLD
}

/**
 * Searchable inventory picker. Agents should never have to type an IMEI or a
 * device name: they pick the physical device from a live list fetched from the
 * server. Scanning / manual entry remain as clearly-marked rescue paths only.
 */
@Composable
fun DevicePickerDialog(
    devices: List<Device>,
    filter: DevicePickerFilter,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelect: (Device) -> Unit,
    onDismiss: () -> Unit,
    onScanRequested: (() -> Unit)? = null,
    onManualRequested: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }

    val eligible = remember(devices, filter) {
        devices.filter { device ->
            when (filter) {
                DevicePickerFilter.IN_STOCK -> device.status != "sold"
                DevicePickerFilter.SOLD -> device.status == "sold"
            }
        }
    }
    val visible = remember(eligible, query) {
        val q = query.trim()
        if (q.isEmpty()) eligible
        else eligible.filter {
            it.model.contains(q, ignoreCase = true) ||
                it.imei.contains(q) ||
                (it.customerName?.contains(q, ignoreCase = true) == true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (filter) {
                                DevicePickerFilter.IN_STOCK -> "Select device from stock"
                                DevicePickerFilter.SOLD -> "Select enrolled device"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (filter) {
                                DevicePickerFilter.IN_STOCK -> "Only devices not yet sold are shown."
                                DevicePickerFilter.SOLD -> "Devices with an active account."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh inventory",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search model, customer or IMEI") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(360.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                when {
                    isLoading && visible.isEmpty() -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Loading inventory…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    visible.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (query.isBlank()) {
                                    when (filter) {
                                        DevicePickerFilter.IN_STOCK -> "No unsold devices in your inventory."
                                        DevicePickerFilter.SOLD -> "No enrolled devices yet."
                                    }
                                } else {
                                    "No device matches \"$query\"."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (query.isBlank() && filter == DevicePickerFilter.IN_STOCK) {
                                Text(
                                    "Add stock from the Inventory tab first, or use a rescue option below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = visible,
                                key = { it.id.ifBlank { it.imei } }
                            ) { device ->
                                DevicePickerRow(
                                    device = device,
                                    filter = filter,
                                    onClick = { onSelect(device) }
                                )
                            }
                        }
                    }
                }

                if (onScanRequested != null || onManualRequested != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onScanRequested != null) {
                            TextButton(onClick = onScanRequested) {
                                Icon(
                                    Icons.Filled.QrCode2,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan barcode")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        if (onManualRequested != null) {
                            TextButton(onClick = onManualRequested) {
                                Text("Enter manually")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicePickerRow(
    device: Device,
    filter: DevicePickerFilter,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_device),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.model.ifBlank { "Device" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    device.imei,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (filter == DevicePickerFilter.SOLD && !device.customerName.isNullOrBlank()) {
                    Text(
                        device.customerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StockChip(filter = filter)
        }
    }
}

@Composable
private fun StockChip(filter: DevicePickerFilter) {
    val (label, bg, fg) = when (filter) {
        DevicePickerFilter.IN_STOCK -> Triple(
            "In stock",
            Color(0xFF10B981).copy(alpha = 0.15f),
            Color(0xFF059669)
        )
        DevicePickerFilter.SOLD -> Triple(
            "Enrolled",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary
        )
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(360.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
    }
}
