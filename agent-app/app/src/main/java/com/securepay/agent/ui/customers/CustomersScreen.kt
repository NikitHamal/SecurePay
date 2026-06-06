package com.securepay.agent.ui.customers

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.securepay.agent.data.model.Account
import com.securepay.agent.data.model.AccountStatus
import com.securepay.agent.data.model.displayStatus
import com.securepay.agent.data.model.formatAmount
import com.securepay.agent.data.remote.SecurePayRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    repository: SecurePayRepository,
    onBack: () -> Unit,
    onCustomerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        isLoading = true
        scope.launch {
            val result = repository.listAccounts(statusFilter)
            isLoading = false
            result.fold(
                onSuccess = { accounts = it },
                onFailure = { error = it.message }
            )
        }
    }

    LaunchedEffect(statusFilter) { load() }

    val filteredAccounts = accounts.filter { account ->
        if (searchQuery.isBlank()) true
        else account.customerName.contains(searchQuery, ignoreCase = true) ||
                account.id.contains(searchQuery, ignoreCase = true) ||
                account.phoneNumber.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Customers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search customers...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = statusFilter == "ACTIVE",
                    onClick = { statusFilter = "ACTIVE" },
                    label = { Text("Active") }
                )
                FilterChip(
                    selected = statusFilter == "WARNING",
                    onClick = { statusFilter = "WARNING" },
                    label = { Text("Warning") }
                )
                FilterChip(
                    selected = statusFilter == "LOCKED",
                    onClick = { statusFilter = "LOCKED" },
                    label = { Text("Locked") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    Text(error ?: "Error loading accounts", color = MaterialTheme.colorScheme.error)
                }
            } else if (filteredAccounts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.People,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No customers found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredAccounts, key = { it.id }) { account ->
                        CustomerRow(
                            account = account,
                            onClick = { onCustomerClick(account.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(
    account: Account,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.customerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${account.id} · ${account.deviceModel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Balance: ${formatAmount(account.remainingBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(status = account.status)
        }
    }
}

@Composable
private fun StatusBadge(status: AccountStatus) {
    val (text, color) = when (status) {
        AccountStatus.ACTIVE -> "Active" to MaterialTheme.colorScheme.primary
        AccountStatus.WARNING -> "Warning" to MaterialTheme.colorScheme.tertiary
        AccountStatus.LOCKED -> "Locked" to MaterialTheme.colorScheme.error
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}