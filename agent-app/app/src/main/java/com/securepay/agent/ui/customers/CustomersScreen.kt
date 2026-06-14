package com.securepay.agent.ui.customers

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.securepay.agent.data.model.Account
import com.securepay.agent.data.model.AccountStatus
import com.securepay.agent.data.model.displayStatus
import com.securepay.agent.data.model.formatAmount
import com.securepay.agent.data.remote.SecurePayRepository
import com.securepay.agent.R
import com.securepay.agent.ui.theme.SecurePayAgentTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    repository: SecurePayRepository?,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onCustomerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current

    fun load() {
        if (isPreview) {
            isLoading = false
            accounts = listOf(
                Account(id = "1", customerName = "John Doe", phoneNumber = "0711223344", deviceModel = "TECNO KL4", remainingBalance = 1500000, status = AccountStatus.ACTIVE),
                Account(id = "2", customerName = "Jane Smith", phoneNumber = "0722334455", deviceModel = "Samsung A14", remainingBalance = 2000000, status = AccountStatus.WARNING)
            )
            return
        }
        isLoading = true
        scope.launch {
            val result = repository?.listAccounts(statusFilter) ?: return@launch
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

    val backgroundColor = Color(0xFF212121)
    val cardColor = Color(0xFF2A2A2A)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,

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
                        selected = true,
                        onClick = { },
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_customers), contentDescription = "Customers", modifier = Modifier.size(20.dp)) },
                        label = { Text("Customers") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            indicatorColor = Color(0xFF10B981).copy(alpha = 0.1f)
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToInventory,
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_inventory), contentDescription = "Inventory", modifier = Modifier.size(20.dp)) },
                        label = { Text("Inventory") }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search customers...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = cardColor,
                    unfocusedContainerColor = cardColor,
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(360.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    null to "All",
                    "ACTIVE" to "Active",
                    "WARNING" to "Warning",
                    "LOCKED" to "Locked"
                )
                filters.forEach { (filterValue, label) ->
                    val isSelected = statusFilter == filterValue
                    FilterChip(
                        selected = isSelected,
                        onClick = { statusFilter = filterValue },
                        label = { Text(label, color = if (isSelected) Color.White else Color.Gray) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = cardColor,
                            selectedContainerColor = Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                }
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
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No customers found", color = Color.Gray)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
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
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${account.id} · ${account.deviceModel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "Balance: ${formatAmount(account.remainingBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            StatusBadge(status = account.status)
        }
    }
}

@Composable
private fun StatusBadge(status: AccountStatus) {
    val (text, color) = when (status) {
        AccountStatus.ACTIVE -> "Active" to Color(0xFF10B981)
        AccountStatus.WARNING -> "Warning" to Color(0xFFFDE047)
        AccountStatus.LOCKED -> "Locked" to Color(0xFFFDA4AF)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CustomersScreenPreview() {
    SecurePayAgentTheme {
        CustomersScreen(
            repository = null,
            onBack = {},
            onNavigateToHome = {},
            onNavigateToInventory = {},
            onNavigateToLedger = {},
            onCustomerClick = {}
        )
    }
}