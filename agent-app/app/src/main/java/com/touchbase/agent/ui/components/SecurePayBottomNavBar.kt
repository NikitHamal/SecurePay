package com.touchbase.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class TabItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun SecurePayBottomNavBar(
    selectedTab: Int,
    onHomeClick: () -> Unit,
    onCustomersClick: () -> Unit,
    onInventoryClick: () -> Unit,
    onLedgerClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = MaterialTheme.colorScheme.surface
    val indicatorColor = if (isDark) Color(0xFF004B30) else Color(0xFFB5D8C7)
    val selectedIconColor = if (isDark) Color(0xFF34D399) else Color(0xFF004B30)
    val unselectedIconColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563)

    val items = listOf(
        TabItem("Home", Icons.Outlined.Home, onHomeClick),
        TabItem("Customers", Icons.Outlined.People, onCustomersClick),
        TabItem("Stock", Icons.Outlined.Inbox, onInventoryClick),
        TabItem("Ledger", Icons.Outlined.Receipt, onLedgerClick),
        TabItem("More", Icons.Outlined.MoreHoriz, onMoreClick)
    )

    Box(modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = containerColor,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                modifier = Modifier.height(64.dp)
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = item.onClick,
                        icon = {
                            Box(
                                modifier = if (selected) {
                                    Modifier.background(indicatorColor, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 16.dp, vertical = 9.dp)
                                } else {
                                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selected) selectedIconColor else unselectedIconColor
                                )
                            }
                        },
                        label = { },
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedTextColor = selectedIconColor,
                            unselectedTextColor = unselectedIconColor
                        )
                    )
                }
            }
        }
    }
}
