package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.touchbase.agent.ui.enrollment.EnrollmentStep

/** Icon for each M-KOPA section header (used by the wizard chrome). */
fun sectionIcon(step: EnrollmentStep): ImageVector = when (step) {
    EnrollmentStep.CUSTOMER, EnrollmentStep.DETAILS -> Icons.Outlined.Person
    EnrollmentStep.CONTACTS -> Icons.Outlined.Group
    EnrollmentStep.IDENTITY, EnrollmentStep.VERIFY -> Icons.Outlined.Fingerprint
    EnrollmentStep.LOCATION -> Icons.Outlined.LocationOn
    EnrollmentStep.PRODUCT, EnrollmentStep.OFFERS, EnrollmentStep.LOAN -> Icons.Outlined.PhoneAndroid
    else -> Icons.Outlined.Person
}

/**
 * M-KOPA intro screen ("You're onboarding a new customer / Let's complete
 * their onboarding.") — hero art + message; the wizard's CONTINUE button
 * sits at the bottom.
 */
@Composable
fun IntroStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Hero: concentric tinted circles with a person, echoing the M-KOPA illustration.
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(86.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            "You're onboarding a new customer",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Let's complete their onboarding.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
