package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.touchbase.agent.ui.enrollment.EnrollmentUiState

/**
 * Personal references screen (our context on top of the M-KOPA flow):
 * next of kin, referee and the guarantor who co-signs the agreement.
 *
 * Per the client every block is OPTIONAL: the agent may enrol a customer
 * without any references at all. A block only has to be completed once the
 * agent starts typing into it — the wizard's Next-button validation treats a
 * completely empty block as skipped.
 */
@Composable
fun ContactsStep(
    state: EnrollmentUiState,
    onKinNameChange: (String) -> Unit,
    onKinRelationChange: (String) -> Unit,
    onKinPhoneChange: (String) -> Unit,
    onRefereeNameChange: (String) -> Unit,
    onRefereePhoneChange: (String) -> Unit,
    onGuarantorNameChange: (String) -> Unit,
    onGuarantorRelationChange: (String) -> Unit,
    onGuarantorPhoneChange: (String) -> Unit,
    onGuarantorIdChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ReferenceHeader("Next of kin", optional = true)

        WizardTextField(
            label = "Full name",
            value = draft.nextOfKinName,
            onValueChange = onKinNameChange,
            placeholder = "e.g. Ama Mensah",
            isError = draft.nextOfKinName.isNotEmpty() && !state.isNextOfKinNameValid,
            supportingText = "Enter at least 3 characters"
        )

        WizardDropdown(
            label = "Relationship",
            value = draft.nextOfKinRelation,
            options = EnrollmentUiState.RELATIONS,
            onSelect = onKinRelationChange
        )

        WizardTextField(
            label = "Phone number",
            value = draft.nextOfKinPhone,
            onValueChange = onKinPhoneChange,
            placeholder = "e.g. 024 123 4567",
            keyboardType = KeyboardType.Phone,
            isError = draft.nextOfKinPhone.isNotEmpty() && !state.isNextOfKinPhoneValid,
            supportingText = "Valid phone, different from the customer's"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))

        ReferenceHeader("Referee", optional = true)

        WizardTextField(
            label = "Full name",
            value = draft.refereeName,
            onValueChange = onRefereeNameChange,
            placeholder = "e.g. Kwame Owusu",
            isError = draft.refereeName.isNotEmpty() && !state.isRefereeNameValid,
            supportingText = "Enter at least 3 characters"
        )

        WizardTextField(
            label = "Phone number",
            value = draft.refereePhone,
            onValueChange = onRefereePhoneChange,
            placeholder = "e.g. 055 987 6543",
            keyboardType = KeyboardType.Phone,
            isError = draft.refereePhone.isNotEmpty() && !state.isRefereePhoneValid,
            supportingText = "Must differ from the customer and next of kin"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))

        ReferenceHeader("Guarantor (co-signer)", optional = true)
        Text(
            "Optional: only fill in when the customer has a co-signer. The guarantor co-signs the financing agreement and can be contacted if the customer defaults.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        WizardTextField(
            label = "Full name",
            value = draft.guarantorName,
            onValueChange = onGuarantorNameChange,
            placeholder = "e.g. Kofi Boateng",
            isError = draft.guarantorName.isNotEmpty() && !state.isGuarantorNameValid,
            supportingText = "Enter at least 3 characters"
        )

        WizardDropdown(
            label = "Relationship",
            value = draft.guarantorRelation,
            options = EnrollmentUiState.RELATIONS,
            onSelect = onGuarantorRelationChange
        )

        WizardTextField(
            label = "Phone number",
            value = draft.guarantorPhone,
            onValueChange = onGuarantorPhoneChange,
            placeholder = "e.g. 020 555 0182",
            keyboardType = KeyboardType.Phone,
            isError = draft.guarantorPhone.isNotEmpty() && !state.isGuarantorPhoneValid,
            supportingText = "Valid phone, different from the customer's"
        )

        WizardTextField(
            label = "ID number",
            value = draft.guarantorIdNumber,
            onValueChange = onGuarantorIdChange,
            placeholder = "Ghana Card / Voter / Passport number",
            isError = draft.guarantorIdNumber.isNotEmpty() && !state.isGuarantorIdValid,
            supportingText = "Enter 4–24 characters"
        )
    }
}

@Composable
private fun ReferenceHeader(title: String, optional: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (optional) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "(optional)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
