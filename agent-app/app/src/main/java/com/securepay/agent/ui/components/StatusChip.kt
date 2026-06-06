package com.securepay.agent.ui.components

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
import com.securepay.agent.data.model.EnrollmentStatus
import com.securepay.agent.ui.theme.Amber
import com.securepay.agent.ui.theme.EmeraldGreen
import com.securepay.agent.ui.theme.VividCrimson

/**
 * Reusable pill that renders an [EnrollmentStatus] using the shared palette.
 */
@Composable
fun StatusChip(
    status: EnrollmentStatus,
    modifier: Modifier = Modifier
) {
    val color: Color = when (status) {
        EnrollmentStatus.ACTIVE -> EmeraldGreen
        EnrollmentStatus.WARNING -> Amber
        EnrollmentStatus.LOCKED -> VividCrimson
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
