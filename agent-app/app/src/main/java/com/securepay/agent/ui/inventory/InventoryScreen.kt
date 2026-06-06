package com.securepay.agent.ui.inventory

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.securepay.agent.data.model.Device
import com.securepay.agent.data.remote.SecurePayRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    repository: SecurePayRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        isLoading = true
        scope.launch {
            val result = repository.listDevices()
            isLoading = false
            result.fold(
                onSuccess = { devices = it },
                onFailure = { error = it.message }
            )
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add device")
                Text("Add Device", modifier = Modifier.padding(start = 8.dp))
            }
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

        if (error != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        if (devices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Devices, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No devices in inventory", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices, key = { it.id }) { device ->
                DeviceCard(device = device)
            }
        }
    }

    if (showAddDialog) {
        AddDeviceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { imei, model ->
                scope.launch {
                    repository.addDevice(imei, model)
                    showAddDialog = false
                    load()
                }
            }
        )
    }
}

@Composable
private fun DeviceCard(device: Device, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.model,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "IMEI: ${device.imei}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DeviceStatusBadge(status = device.status)
        }
    }
}

@Composable
private fun DeviceStatusBadge(status: String) {
    val (label, color) = when (status) {
        "in_stock" -> "In Stock" to MaterialTheme.colorScheme.primary
        "sold" -> "Sold" to MaterialTheme.colorScheme.tertiary
        "recalled" -> "Recalled" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var imei by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = imei,
                    onValueChange = { imei = it.filter { c -> c.isDigit() }.take(15) },
                    label = { Text("IMEI (15 digits)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Device model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (imei.length != 15) { errorMessage = "IMEI must be 15 digits"; return@Button }
                    if (model.isBlank()) { errorMessage = "Model is required"; return@Button }
                    onAdd(imei, model)
                }
            ) { Text("Add") }
        },
        dismissButton = {
            androidx.compose.material3.OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}