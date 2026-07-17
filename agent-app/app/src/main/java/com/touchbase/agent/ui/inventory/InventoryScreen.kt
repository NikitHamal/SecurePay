package com.touchbase.agent.ui.inventory

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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.foundation.layout.width
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import android.app.Activity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import com.touchbase.agent.R
import com.touchbase.agent.data.model.Device
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.components.BarcodeScannerSheet
import com.touchbase.agent.ui.components.SecurePayBottomNavBar
import com.touchbase.agent.ui.components.ButtonText

import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.isLight
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    repository: SecurePayRepository?,
    onNavigateToHome: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToMore: () -> Unit,
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
    val backgroundColor = MaterialTheme.colorScheme.background

    if (!isPreview) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = backgroundColor.isLight()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Inventory", color = MaterialTheme.colorScheme.onBackground) },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_device),
                            contentDescription = "Add device",
                            tint = MaterialTheme.colorScheme.onBackground,
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
            SecurePayBottomNavBar(
                selectedTab = 2,
                onHomeClick = onNavigateToHome,
                onCustomersClick = onNavigateToCustomers,
                onInventoryClick = {},
                onLedgerClick = onNavigateToLedger,
                onMoreClick = onNavigateToMore
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
                DeviceCard(
                    device = device,
                    onDelete = {
                        if (repository != null) {
                            scope.launch {
                                val result = repository.deleteDevice(device.id)
                                result.fold(
                                    onSuccess = { load() },
                                    onFailure = {
                                        error = it.message
                                        load()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddDeviceBottomSheet(
            onDismiss = { showAddDialog = false },
            onAdd = { imei, model ->
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
private fun DeviceCard(device: Device, modifier: Modifier = Modifier, onDelete: (() -> Unit)? = null) {
    val dateStr = remember(device.soldAt, device.createdAt) {
        val epoch = if (device.soldAt != null && device.soldAt > 0L) device.soldAt else device.createdAt
        runCatching {
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochMilli(epoch))
        }.getOrDefault("")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.model,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "# ${device.imei}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeviceStatusBadge(status = device.status)
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (device.status == "sold" || device.customerName != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.customerName ?: "Unknown Customer",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Inbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Available in Stock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeviceStatusBadge(status: String) {
    val (label, color, icon) = when (status) {
        "in_stock" -> Triple("In Stock", MaterialTheme.colorScheme.primary, Icons.Filled.CheckCircle)
        "sold" -> Triple("Sold", MaterialTheme.colorScheme.primary, Icons.Filled.CheckCircle)
        "recalled" -> Triple("Recalled", MaterialTheme.colorScheme.error, Icons.Filled.Warning)
        else -> Triple(status, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.Warning)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeviceBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var imei by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Add Device", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)

            // Quick scan action — biggest time-saver for stock intake.
            androidx.compose.material3.Button(
                onClick = { showScanner = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(360.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                ButtonText("Scan IMEI Barcode")
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("IMEI (15 digits)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = imei,
                    onValueChange = { imei = it.filter { c -> c.isDigit() }.take(15) },
                    placeholder = { Text("Scan or enter IMEI", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(360.dp),
                    trailingIcon = {
                        IconButton(onClick = { showScanner = true }) {
                            Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = "Scan IMEI",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    supportingText = {
                        Text("${imei.length}/15 digits", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Device model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    placeholder = { Text("e.g. SM-A075F/DS", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(360.dp)
                )
            }
            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Button(
                onClick = {
                    if (imei.length != 15) { errorMessage = "IMEI must be 15 digits"; return@Button }
                    if (model.isBlank()) { errorMessage = "Model is required"; return@Button }
                    onAdd(imei, model)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(360.dp)
            ) { ButtonText("Add to Inventory") }
        }
    }

    if (showScanner) {
        BarcodeScannerSheet(
            title = "Scan IMEI",
            subtitle = "Point camera at the IMEI barcode on the box or sticker. 15-digit codes are detected automatically.",
            onDismiss = { showScanner = false },
            onScan = { digits ->
                imei = digits
                showScanner = false
            }
        )
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
            onNavigateToLedger = {},
            onNavigateToMore = {}
        )
    }
}