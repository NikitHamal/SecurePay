package com.touchbase.agent.ui.customers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Receipt
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.touchbase.agent.data.model.Account
import com.touchbase.agent.data.model.AccountStatus
import com.touchbase.agent.data.model.displayStatus
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.R
import androidx.compose.foundation.isSystemInDarkTheme
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
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
        if (isPreview) return
        isLoading = true
        scope.launch {
            val result = repository?.listAccounts(statusFilter) ?: run {
                isLoading = false
                return@launch
            }
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

    val backgroundColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceVariant

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,

        bottomBar = {
            val isDark = isSystemInDarkTheme()
            val containerColor = if (isDark) Color(0xFF1E1E1E) else Color.White
            val selectedIndicatorColor = if (isDark) Color(0xFF004B30) else Color(0xFFB5D8C7)
            val selectedIconColor = if (isDark) Color(0xFF34D399) else Color(0xFF004B30)
            val unselectedIconColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563)

            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = containerColor,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .height(80.dp)
                ) {
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToHome,
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                        icon = {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Home,
                                    contentDescription = "Dashboard",
                                    modifier = Modifier.size(24.dp),
                                    tint = unselectedIconColor
                                )
                            }
                        },
                        label = null,
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .background(selectedIndicatorColor, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.People,
                                    contentDescription = "Customers",
                                    modifier = Modifier.size(24.dp),
                                    tint = selectedIconColor
                                )
                            }
                        },
                        label = null,
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToInventory,
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                        icon = {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Inbox,
                                    contentDescription = "Inventory",
                                    modifier = Modifier.size(24.dp),
                                    tint = unselectedIconColor
                                )
                            }
                        },
                        label = null,
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToLedger,
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                        icon = {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Receipt,
                                    contentDescription = "Ledger",
                                    modifier = Modifier.size(24.dp),
                                    tint = unselectedIconColor
                                )
                            }
                        },
                        label = null,
                        alwaysShowLabel = false
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
            Spacer(modifier = Modifier.height(16.dp))

            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(cardColor, RoundedCornerShape(360.dp)),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp).size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Search customers...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                            innerTextField()
                        }
                    }
                }
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
                        label = { Text(label, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = cardColor,
                            selectedContainerColor = MaterialTheme.colorScheme.primary
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    color = MaterialTheme.colorScheme.onSurface
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
    val isDark = isSystemInDarkTheme()
    val (text, color) = when (status) {
        AccountStatus.ACTIVE -> "Active" to (if (isDark) Color(0xFF10B981) else Color(0xFF047857))
        AccountStatus.WARNING -> "Warning" to (if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309))
        AccountStatus.LOCKED -> "Locked" to (if (isDark) Color(0xFFFDA4AF) else Color(0xFFB91C1C))
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
