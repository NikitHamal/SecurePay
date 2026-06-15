package com.securepay.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.securepay.agent.R

/**
 * A horizontal three-step indicator. The active step is emerald, completed
 * steps stay emerald, and upcoming steps render muted.
 */
@Composable
fun StepIndicator(
    currentIndex: Int,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        labels.forEachIndexed { index, label ->
            val isCompleted = index < currentIndex
            val isDone = index <= currentIndex
            val targetColor =
                if (isDone) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            val barColor by animateColorAsState(targetColor, label = "stepBarColor")

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCompleted) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check_filled),
                            contentDescription = "Completed",
                            modifier = Modifier.size(18.dp),
                            tint = Color.Unspecified
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(barColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isDone) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = if (index == currentIndex) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isDone) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
