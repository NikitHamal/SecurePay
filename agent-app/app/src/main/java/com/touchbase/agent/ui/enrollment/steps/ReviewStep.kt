package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.touchbase.agent.R
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.ui.enrollment.EnrollmentStep
import com.touchbase.agent.ui.enrollment.EnrollmentUiState

/**
 * Final "verification result" step, modelled on the M-KOPA application flow:
 * a strong status banner followed by a checklist of everything the system
 * verified, each ending in a Passed / Missing status. Tapping a row jumps back
 * to that step so the agent can correct it.
 */
@Composable
fun ReviewStep(
    state: EnrollmentUiState,
    onEditStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val allReady = state.isSubmitReady

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (allReady) Color(0xFF22C55E)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (allReady) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (allReady) "Ready to submit" else "Checks incomplete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (allReady) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (allReady) "All application checks passed"
                        else "Complete every item below, then submit",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allReady) Color.White.copy(alpha = 0.9f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Verification checklist
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ReviewRow(
                    iconRes = R.drawable.ic_customers,
                    title = "Identity document",
                    subtitle = "${state.draft.customerName.trim()} • ${state.draft.idType.ifBlank { "ID" }} ${state.draft.nationalId.trim()}",
                    passed = state.isNameValid && state.isNationalIdValid && state.isIdTypeValid,
                    onClick = { onEditStep(EnrollmentStep.ordered.indexOf(EnrollmentStep.KYC)) }
                )
                ReviewDivider()
                ReviewRow(
                    iconRes = R.drawable.ic_customers,
                    title = "Customer photo",
                    subtitle = if (state.draft.customerPhotoBase64 != null) "Selfie attached" else "Selfie required",
                    passed = state.draft.customerPhotoBase64 != null,
                    onClick = { onEditStep(EnrollmentStep.ordered.indexOf(EnrollmentStep.KYC)) }
                )
                ReviewDivider()
                ReviewRow(
                    iconRes = R.drawable.ic_check,
                    title = "ID card photos",
                    subtitle = when {
                        state.draft.nationalIdFrontBase64 != null && state.draft.nationalIdBackBase64 != null -> "Front and back attached"
                        state.draft.nationalIdFrontBase64 != null || state.draft.nationalIdBackBase64 != null -> "One side missing"
                        else -> "Front and back required"
                    },
                    passed = state.draft.nationalIdFrontBase64 != null && state.draft.nationalIdBackBase64 != null,
                    onClick = { onEditStep(EnrollmentStep.ordered.indexOf(EnrollmentStep.KYC)) }
                )
                ReviewDivider()
                ReviewRow(
                    iconRes = R.drawable.ic_customers,
                    title = "Next of kin & referee",
                    subtitle = if (state.draft.nextOfKinName.isNotBlank())
                        "${state.draft.nextOfKinName.trim()} (${state.draft.nextOfKinRelation.ifBlank { "—" }})"
                    else "Not captured",
                    passed = state.isReferencesStepValid,
                    onClick = { onEditStep(EnrollmentStep.ordered.indexOf(EnrollmentStep.REFERENCES)) }
                )
                ReviewDivider()
                ReviewRow(
                    iconRes = R.drawable.ic_customers,
                    title = "Guarantor (co-signer)",
                    subtitle = state.draft.guarantorName.trim().ifBlank { "Not captured" },
                    passed = state.isSignerStepValid,
                    onClick = { onEditStep(EnrollmentStep.ordered.indexOf(EnrollmentStep.SIGNER)) }
                )
                ReviewDivider()
                ReviewRow(
                    iconRes = R.drawable.ic_check,
                    title = "Consent & signature",
                    subtitle = if (state.isConsentStepValid) "Both consents given, signature on file"
                    else "Both consents + signature required",
                    passed = state.isConsentStepValid,
                    onClick = { onEditStep(EnrollmentStep.ordered.indexOf(EnrollmentStep.CONSENT)) }
                )
                ReviewDivider()
                ReviewRow(
                    iconRes = R.drawable.ic_device,
                    title = "Device",
                    subtitle = if (state.isImeiValid) "${state.draft.deviceModel} • ${state.draft.imei}"
                    else "No device selected",
                    passed = state.isDeviceStepValid,
                    subtitleMono = state.isImeiValid,
                    onClick = { onEditStep(EnrollmentStep.ordered.indexOf(EnrollmentStep.DEVICE)) }
                )
                ReviewDivider()
                ReviewRow(
                    iconRes = R.drawable.ic_ledger,
                    title = "Financing plan",
                    subtitle = planSubtitle(state),
                    passed = state.isPlanStepValid,
                    onClick = { onEditStep(EnrollmentStep.ordered.indexOf(EnrollmentStep.PLAN)) }
                )
            }
        }

        Text(
            "Tap any row to review or fix it. When every row shows Passed, hit Submit to create the customer's account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun planSubtitle(state: EnrollmentUiState): String {
    val plan = state.selectedPlan
    return if (plan != null) {
        "${plan.name} — ${formatAmount(plan.totalAmount)} over ${plan.termDays} days"
    } else if (state.draft.totalLoanAmount > 0) {
        "Custom — ${formatAmount(state.draft.totalLoanAmount)} over ${state.draft.termDays} days"
    } else {
        "No plan selected"
    }
}

@Composable
private fun ReviewDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = 1.dp
    )
}

@Composable
private fun ReviewRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    passed: Boolean,
    onClick: () -> Unit,
    subtitleMono: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = if (subtitleMono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (passed) Color(0xFF059669) else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                if (passed) "Passed" else "Missing",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (passed) Color(0xFF059669) else MaterialTheme.colorScheme.error
            )
        }
    }
}
