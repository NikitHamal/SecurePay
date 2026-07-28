package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import com.touchbase.agent.ui.components.DatePickerField
import com.touchbase.agent.ui.enrollment.GhanaGeo

/**
 * M-KOPA "Customer information" form B: Date of Birth, Marital Status,
 * Employment status, Gender radios, "Is the customer the user?" radios.
 */
@Composable
fun DetailsStep(
    state: EnrollmentUiState,
    onDateOfBirthChange: (String) -> Unit,
    onMaritalChange: (String) -> Unit,
    onEmploymentChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onIsCustomerUserChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DatePickerField(
            label = "Date of Birth",
            value = draft.dateOfBirth,
            onValueChange = onDateOfBirthChange,
            placeholder = "DD/MM/YYYY"
        )

        WizardDropdown(
            label = "Marital Status",
            value = draft.maritalStatus,
            options = GhanaGeo.MARITAL_STATUSES,
            onSelect = onMaritalChange
        )

        WizardDropdown(
            label = "Employment status",
            value = draft.employmentStatus,
            options = GhanaGeo.EMPLOYMENT_STATUSES,
            onSelect = onEmploymentChange
        )

        WizardRadioRow(
            label = "Gender",
            options = EnrollmentUiState.GENDERS,
            selected = draft.gender,
            onSelect = onGenderChange
        )

        WizardRadioRow(
            label = "Is the customer the user?",
            options = listOf("Yes", "No"),
            selected = when (draft.isCustomerUser) {
                true -> "Yes"
                false -> "No"
                null -> ""
            },
            onSelect = { onIsCustomerUserChange(it == "Yes") }
        )
    }
}
