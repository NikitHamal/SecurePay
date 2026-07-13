package com.touchbase.user.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun CustomerBottomBar(
    selected: String,
    onHome: () -> Unit,
    onPayments: () -> Unit,
    onMore: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == "home",
            onClick = onHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = selected == "payments",
            onClick = onPayments,
            icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null) },
            label = { Text("Payments") }
        )
        NavigationBarItem(
            selected = selected == "more",
            onClick = onMore,
            icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
            label = { Text("More") }
        )
    }
}
