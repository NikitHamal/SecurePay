package com.securepay.customer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.securepay.customer.data.model.DeviceStatus
import com.securepay.customer.ui.theme.ErrorCrimson
import com.securepay.customer.ui.theme.PrimaryEmerald
import com.securepay.customer.ui.theme.WarningAmber

/**
 * Compact pill chip that surfaces the live [DeviceStatus], colored per the
 * shared palette: Emerald (ACTIVE), Amber (WARNING), Crimson (LOCKED).
 */
@Composable
fun StatusChip(
    status: DeviceStatus,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (status) {
        DeviceStatus.ACTIVE -> "ACTIVE" to PrimaryEmerald
        DeviceStatus.WARNING -> "PAYMENT DUE SOON" to WarningAmber
        DeviceStatus.LOCKED -> "LOCKED" to ErrorCrimson
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = Color.Black,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(PaddingValues(horizontal = 14.dp, vertical = 6.dp))
    )
}
