package com.securepay.agent.ui.ledger

import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.securepay.agent.R
import com.securepay.agent.data.model.LedgerEntry
import com.securepay.agent.data.model.formatAmount
import com.securepay.agent.data.remote.SecurePayRepository
import com.securepay.agent.ui.theme.SecurePayAgentTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    repository: SecurePayRepository?,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToInventory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    var error by remember { mutableStateOf<String?>(null) }
    var methodFilter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (isPreview) {
            entries = listOf(
                LedgerEntry(id = "1", customerName = "John Doe", amount = 1500, method = "mpesa", reference = "TRX123", dateEpochMillis = System.currentTimeMillis()),
                LedgerEntry(id = "2", customerName = "Jane Smith", amount = 500, method = "cash", dateEpochMillis = System.currentTimeMillis() - 86400000)
            )
            return
        }
        isLoading = true
        scope.launch {
            val result = repository?.listLedger(methodFilter)
            isLoading = false
            result?.fold(
                onSuccess = { entries = it },
                onFailure = { error = it.message }
            )
        }
    }

    LaunchedEffect(methodFilter) { load() }

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
                title = { Text("Payment Ledger", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    scrolledContainerColor = backgroundColor
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
                    modifier = Modifier.padding(bottom = 0.dp)
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
                        selected = false,
                        onClick = onNavigateToInventory,
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_inventory), contentDescription = "Inventory", modifier = Modifier.size(20.dp)) },
                        label = { Text("Inventory") }
                    )
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_ledger), contentDescription = "Ledger", modifier = Modifier.size(20.dp)) },
                        label = { Text("Ledger") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            indicatorColor = Color(0xFF10B981).copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                val filters = listOf(
                    null to "All",
                    "mpesa" to "M-Pesa",
                    "cash" to "Cash",
                    "bank_transfer" to "Bank"
                )
                filters.forEach { (filterValue, label) ->
                    val isSelected = methodFilter == filterValue
                    FilterChip(
                        selected = isSelected,
                        onClick = { methodFilter = filterValue },
                        label = { Text(label, color = if (isSelected) Color.White else Color.Gray) },
                        shape = RoundedCornerShape(360.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF2A2A2A),
                            selectedContainerColor = Color(0xFF10B981)
                        )
                    )
                }
            }

            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
            } else if (entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No payments recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        LedgerEntryCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerEntryCard(entry: LedgerEntry, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.customerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = formatAmount(entry.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${entry.method.uppercase()} · ${entry.reference.ifBlank { "N/A" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDate(entry.dateEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdf.format(Date(epochMillis))
    } catch (_: Exception) {
        "—"
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LedgerScreenPreview() {
    SecurePayAgentTheme {
        LedgerScreen(
            repository = null,
            onBack = {},
            onNavigateToHome = {},
            onNavigateToCustomers = {},
            onNavigateToInventory = {}
        )
    }
}