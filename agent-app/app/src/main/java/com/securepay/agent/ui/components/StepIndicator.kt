package com.securepay.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.securepay.agent.data.model.WizardStep

/**
 * Custom M3 stepper showing the three enrollment phases. Completed and active
 * steps glow emerald; upcoming steps stay muted.
 */
@Composable
fun StepIndicator(
    currentStep: WizardStep,
    modifier: Modifier = Modifier
) {
    val steps = WizardStep.ordered
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEachIndexed { i, step ->
            val isCompleted = step.index < currentStep.index
            val isActive = step.index == currentStep.index

            StepNode(
                step = step,
                isCompleted = isCompleted,
                isActive = isActive,
                modifier = Modifier.width(72.dp)
            )

            if (i < steps.lastIndex) {
                StepConnector(
                    filled = step.index < currentStep.index,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 15.dp)
                )
            }
        }
    }
}

@Composable
private fun StepNode(
    step: WizardStep,
    isCompleted: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.surfaceVariant

    val dotColor by animateColorAsState(
        targetValue = if (isCompleted || isActive) activeColor else mutedColor,
        label = "stepDotColor"
    )
    val dotSize by animateDpAsState(
        targetValue = if (isActive) 32.dp else 28.dp,
        label = "stepDotSize"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = (step.index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = step.title,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (isCompleted || isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun StepConnector(
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    val color: Color = if (filled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = modifier
            .height(2.dp)
            .clip(CircleShape)
            .background(color)
    )
}
