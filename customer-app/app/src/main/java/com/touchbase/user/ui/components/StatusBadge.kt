package com.touchbase.user.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.ui.theme.Amber
import com.touchbase.user.ui.theme.Crimson
import com.touchbase.user.ui.theme.Emerald

/** Maps a [DeviceStatus] to its palette colour + label. */
fun statusColor(status: DeviceStatus): Color = when (status) {
    DeviceStatus.ACTIVE -> Emerald
    DeviceStatus.WARNING -> Amber
    DeviceStatus.LOCKED -> Crimson
}

fun statusLabel(status: DeviceStatus): String = when (status) {
    DeviceStatus.ACTIVE -> "ACTIVE"
    DeviceStatus.WARNING -> "PAYMENT DUE SOON"
    DeviceStatus.LOCKED -> "LOCKED"
}

@Composable
fun StatusBadge(
    status: DeviceStatus,
    modifier: Modifier = Modifier
) {
    val color = statusColor(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(PaddingValues(horizontal = 12.dp, vertical = 6.dp))
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "  ${statusLabel(status)}",
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}
