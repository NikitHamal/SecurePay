package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.touchbase.agent.ui.enrollment.EnrollmentUiState

/**
 * M-KOPA "Customer information" form A: First Name, Surname, ID Type
 * (dropdown), ID Number, Phone number and Other Number with the Ghana prefix.
 */
@Composable
fun CustomerStep(
    state: EnrollmentUiState,
    onFirstNameChange: (String) -> Unit,
    onSurnameChange: (String) -> Unit,
    onIdTypeChange: (String) -> Unit,
    onNationalIdChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onOtherPhoneChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        WizardTextField(
            label = "First Name",
            value = draft.firstName,
            onValueChange = onFirstNameChange,
            placeholder = "Daniel",
            isError = draft.firstName.isNotEmpty() && !state.isFirstNameValid,
            supportingText = "Enter at least 2 characters"
        )

        WizardTextField(
            label = "Surname",
            value = draft.surname,
            onValueChange = onSurnameChange,
            placeholder = "Sem",
            isError = draft.surname.isNotEmpty() && !state.isSurnameValid,
            supportingText = "Enter at least 2 characters"
        )

        WizardDropdown(
            label = "ID Type",
            value = draft.idType,
            options = EnrollmentUiState.ID_TYPES,
            onSelect = onIdTypeChange
        )

        WizardTextField(
            label = "ID Number",
            value = draft.nationalId,
            onValueChange = onNationalIdChange,
            placeholder = "GHA-XXXXXXXXX-X",
            isError = draft.nationalId.isNotEmpty() && !state.isNationalIdValid,
            supportingText = "Enter 6–20 characters"
        )

        WizardPhoneField(
            label = "Phone number",
            value = draft.phoneNumber,
            onValueChange = onPhoneChange,
            isError = draft.phoneNumber.isNotEmpty() && !state.isPhoneValid,
            supportingText = "Enter a valid phone number"
        )

        WizardPhoneField(
            label = "Other Number",
            value = draft.otherPhone,
            onValueChange = onOtherPhoneChange,
            isError = draft.otherPhone.isNotEmpty() && !state.isOtherPhoneValid,
            supportingText = "Optional — must differ from the main number"
        )
    }
}
