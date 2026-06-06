package com.securepay.agent.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securepay.agent.data.model.KpiSummary
import com.securepay.agent.data.model.formatAmount
import com.securepay.agent.data.remote.SecurePayRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: SecurePayRepository,
    onNavigateToCustomers: () -> Unit,
    onNavigateToEnrollment: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var kpis by remember { mutableStateOf<KpiSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val dealerName by repository.dealerName.collectAsState()

    LaunchedEffect(Unit) {
        isLoading = true
        val result = repository.getKpis()
        isLoading = false
        result.fold(
            onSuccess = { kpis = it },
            onFailure = { error = it.message }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SecurePay", fontWeight = FontWeight.Bold)
                        Text(
                            dealerName ?: "Agent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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

        if (error != null && kpis == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            kpis?.let { kpi ->
                KpiCardRow(
                    activeCount = kpi.activeCount,
                    lockedCount = kpi.lockedCount,
                    warningCount = kpi.warningCount
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiTile(
                        title = "Outstanding",
                        value = formatAmount(kpi.totalOutstanding),
                        modifier = Modifier.weight(1f)
                    )
                    KpiTile(
                        title = "Collected Today",
                        value = formatAmount(kpi.collectedToday),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            ActionCard(
                icon = Icons.Filled.Add,
                title = "New Enrollment",
                subtitle = "Register a new customer device",
                onClick = onNavigateToEnrollment
            )

            ActionCard(
                icon = Icons.Filled.People,
                title = "Customers",
                subtitle = "View and manage accounts",
                onClick = onNavigateToCustomers
            )

            ActionCard(
                icon = Icons.Filled.Devices,
                title = "Inventory",
                subtitle = "Manage device stock",
                onClick = onNavigateToInventory
            )

            ActionCard(
                icon = Icons.Filled.Receipt,
                title = "Ledger",
                subtitle = "Payment history and records",
                onClick = onNavigateToLedger
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KpiCardRow(
    activeCount: Int,
    lockedCount: Int,
    warningCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusCard(
            title = "Active",
            count = activeCount,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatusCard(
            title = "Warning",
            count = warningCount,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        StatusCard(
            title = "Locked",
            count = lockedCount,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KpiTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}