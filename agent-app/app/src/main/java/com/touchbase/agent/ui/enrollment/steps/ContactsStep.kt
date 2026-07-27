package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.touchbase.agent.ui.components.SelectionChips
import com.touchbase.agent.ui.enrollment.EnrollmentUiState

/**
 * References step (M-KOPA "guarantors & next of kin" screen): the people we can
 * reach if the customer goes quiet. Relation is picked from chips — no typing.
 */
@Composable
fun ContactsStep(
    state: EnrollmentUiState,
    onKinNameChange: (String) -> Unit,
    onKinRelationChange: (String) -> Unit,
    onKinPhoneChange: (String) -> Unit,
    onRefereeNameChange: (String) -> Unit,
    onRefereePhoneChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Next of kin",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        WizardTextField(
            label = "Full name",
            value = draft.nextOfKinName,
            onValueChange = onKinNameChange,
            placeholder = "e.g. Ama Mensah",
            isError = draft.nextOfKinName.isNotEmpty() && !state.isNextOfKinNameValid,
            supportingText = "Enter at least 3 characters"
        )

        SelectionChips(
            label = "Relationship to customer",
            options = EnrollmentUiState.RELATIONS,
            selected = draft.nextOfKinRelation,
            onSelect = onKinRelationChange,
            error = "Pick the relationship"
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

        Text(
            "Referee",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Someone else who knows the customer well — a different person from the next of kin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
    }
}
