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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import com.securepay.agent.R
import com.securepay.agent.data.model.Device
import com.securepay.agent.data.remote.SecurePayRepository
import com.securepay.agent.ui.theme.SecurePayAgentTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    repository: SecurePayRepository?,
    onNavigateToHome: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToLedger: () -> Unit,
    modifier: Modifier = Modifier
) {
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        isLoading = true
        if (repository == null) {
            isLoading = false
            return
        }
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

    val isPreview = LocalInspectionMode.current
    val view = LocalView.current
    val backgroundColor = Color(0xFF212121)

    if (!isPreview) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Inventory", color = Color.White) },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_device),
                            contentDescription = "Add device",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.1f),
                    thickness = 0.5.dp
                )
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToHome,
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_dashboard), contentDescription = "Dashboard", modifier = Modifier.size(20.dp)) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToCustomers,
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_customers), contentDescription = "Customers", modifier = Modifier.size(20.dp)) },
                        label = { Text("Customers") }
                    )
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_inventory), contentDescription = "Inventory", modifier = Modifier.size(20.dp)) },
                        label = { Text("Inventory") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            indicatorColor = Color(0xFF10B981).copy(alpha = 0.1f)
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToLedger,
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_ledger), contentDescription = "Ledger", modifier = Modifier.size(20.dp)) },
                        label = { Text("Ledger") }
                    )
                }
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
                Icon(Icons.Filled.Devices, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No devices in inventory", color = Color.Gray)
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
        AddDeviceBottomSheet(
            onDismiss = { showAddDialog = false },
            onAdd = { imei, model, stock ->
                if (repository != null) {
                    scope.launch {
                        repository.addDevice(imei, model)
                        showAddDialog = false
                        load()
                    }
                } else {
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun DeviceCard(device: Device, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
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
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "IMEI: ${device.imei}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "Stock: ${device.stock}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
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
private fun AddDeviceBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var imei by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF2A2A2A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Add Device", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("IMEI (15 digits)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                OutlinedTextField(
                    value = imei,
                    onValueChange = { imei = it.filter { c -> c.isDigit() }.take(15) },
                    placeholder = { Text("Enter IMEI", color = Color.Gray.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF212121),
                        unfocusedContainerColor = Color(0xFF212121),
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(360.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Text("Device model", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        placeholder = { Text("Enter model", color = Color.Gray.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF212121),
                            unfocusedContainerColor = Color(0xFF212121),
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(360.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Text("Total Stock", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("Enter stock", color = Color.Gray.copy(alpha = 0.5f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF212121),
                            unfocusedContainerColor = Color(0xFF212121),
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(360.dp)
                    )
                }
            }
            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Button(
                onClick = {
                    if (imei.length != 15) { errorMessage = "IMEI must be 15 digits"; return@Button }
                    if (model.isBlank()) { errorMessage = "Model is required"; return@Button }
                    if (stock.isBlank() || stock.toIntOrNull() == null || stock.toInt() <= 0) { errorMessage = "Enter valid stock"; return@Button }
                    onAdd(imei, model, stock)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Add") }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun InventoryScreenPreview() {
    SecurePayAgentTheme {
        InventoryScreen(
            repository = null,
            onNavigateToHome = {},
            onNavigateToCustomers = {},
            onNavigateToLedger = {}
        )
    }
}