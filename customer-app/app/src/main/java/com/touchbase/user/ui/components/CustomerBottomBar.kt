package com.touchbase.user.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CustomerBottomBar(
    selected: String,
    onHome: () -> Unit,
    onPayments: () -> Unit,
    onMore: () -> Unit,
    onAccount: () -> Unit = {}
) {
    val containerColor = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val indicatorColor = primary.copy(alpha = 0.16f)
    val selectedColor = primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    data class Tab(val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)
    val tabs = listOf(
        Tab("home", Icons.Filled.Home, onHome),
        Tab("payments", Icons.Filled.ReceiptLong, onPayments),
        Tab("account", Icons.Filled.Person, onAccount),
        Tab("more", Icons.Filled.MoreHoriz, onMore)
    )

    Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
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
                tabs.forEach { tab ->
                    val sel = selected == tab.key
                    NavigationBarItem(
                        selected = sel,
                        onClick = tab.onClick,
                        icon = {
                            Box(
                                modifier = if (sel) {
                                    Modifier.background(indicatorColor, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 16.dp, vertical = 9.dp)
                                } else {
                                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.key,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (sel) selectedColor else unselectedColor
                                )
                            }
                        },
                        label = {},
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = selectedColor,
                            unselectedIconColor = unselectedColor
                        )
                    )
                }
            }
        }
    }
}
