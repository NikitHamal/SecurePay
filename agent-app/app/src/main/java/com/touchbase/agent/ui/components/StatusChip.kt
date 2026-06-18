package com.touchbase.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.touchbase.agent.data.model.AccountStatus

@Composable
fun StatusChip(
    status: AccountStatus,
    modifier: Modifier = Modifier
) {
    val color: Color = when (status) {
        AccountStatus.ACTIVE -> Color(0xFF10B981)
        AccountStatus.WARNING -> Color(0xFFF59E0B)
        AccountStatus.LOCKED -> Color(0xFFDC2626)
    }

    Text(
        text = status.name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}
