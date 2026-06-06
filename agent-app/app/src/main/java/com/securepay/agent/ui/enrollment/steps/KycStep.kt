package com.securepay.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.securepay.agent.R
import com.securepay.agent.ui.enrollment.EnrollmentUiState

@Composable
fun KycStep(
    state: EnrollmentUiState,
    onNameChange: (String) -> Unit,
    onNationalIdChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = state.draft.customerName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.label_full_name)) },
            singleLine = true,
            isError = state.draft.customerName.isNotEmpty() && !state.isNameValid,
            supportingText = {
                if (state.draft.customerName.isNotEmpty() && !state.isNameValid) {
                    Text("Enter at least 3 characters")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.draft.nationalId,
            onValueChange = onNationalIdChange,
            label = { Text(stringResource(R.string.label_national_id)) },
            singleLine = true,
            isError = state.draft.nationalId.isNotEmpty() && !state.isNationalIdValid,
            supportingText = {
                if (state.draft.nationalId.isNotEmpty() && !state.isNationalIdValid) {
                    Text("Enter 6–20 characters")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.draft.phoneNumber,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.label_phone)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = state.draft.phoneNumber.isNotEmpty() && !state.isPhoneValid,
            supportingText = {
                if (state.draft.phoneNumber.isNotEmpty() && !state.isPhoneValid) {
                    Text("Enter a valid phone number")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}