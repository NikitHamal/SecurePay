package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.touchbase.agent.ui.components.SelectionChips
import com.touchbase.agent.ui.enrollment.EnrollmentUiState

/**
 * Guarantor / co-signer step (the M-KOPA "signer" screen): the person who
 * co-signs the financing agreement and can be contacted on default.
 */
@Composable
fun SignerStep(
    state: EnrollmentUiState,
    onNameChange: (String) -> Unit,
    onRelationChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onIdNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "The guarantor co-signs this agreement. They must be a real, reachable person — the dealer can contact them if the customer defaults.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        WizardTextField(
            label = "Guarantor full name",
            value = draft.guarantorName,
            onValueChange = onNameChange,
            placeholder = "e.g. Kofi Boateng",
            isError = draft.guarantorName.isNotEmpty() && !state.isGuarantorNameValid,
            supportingText = "Enter at least 3 characters"
        )

        SelectionChips(
            label = "Relationship to customer",
            options = EnrollmentUiState.RELATIONS,
            selected = draft.guarantorRelation,
            onSelect = onRelationChange,
            error = "Pick the relationship"
        )

        WizardTextField(
            label = "Guarantor phone number",
            value = draft.guarantorPhone,
            onValueChange = onPhoneChange,
            placeholder = "e.g. 020 555 0182",
            keyboardType = KeyboardType.Phone,
            isError = draft.guarantorPhone.isNotEmpty() && !state.isGuarantorPhoneValid,
            supportingText = "Valid phone, different from the customer's"
        )

        WizardTextField(
            label = "Guarantor ID number",
            value = draft.guarantorIdNumber,
            onValueChange = onIdNumberChange,
            placeholder = "Ghana Card / Voter / Passport number",
            isError = draft.guarantorIdNumber.isNotEmpty() && !state.isGuarantorIdValid,
            supportingText = "Enter 4–24 characters"
        )
    }
}
