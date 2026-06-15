package com.touchbase.agent.ui.enrollment.steps

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.touchbase.agent.R
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import androidx.compose.ui.tooling.preview.Preview
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.enrollment.EnrollmentDraft

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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_full_name), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            OutlinedTextField(
                value = state.draft.customerName,
                onValueChange = onNameChange,
                placeholder = { Text("Enter full name", color = Color.Gray.copy(alpha = 0.5f)) },
                singleLine = true,
                minLines = 1,
                maxLines = 1,
                isError = state.draft.customerName.isNotEmpty() && !state.isNameValid,
                supportingText = {
                    if (state.draft.customerName.isNotEmpty() && !state.isNameValid) {
                        Text("Enter at least 3 characters", color = MaterialTheme.colorScheme.error)
                    }
                },
                textStyle = TextStyle(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF2A2A2A),
                    unfocusedContainerColor = Color(0xFF2A2A2A),
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_national_id), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            OutlinedTextField(
                value = state.draft.nationalId,
                onValueChange = onNationalIdChange,
                placeholder = { Text("Enter national ID", color = Color.Gray.copy(alpha = 0.5f)) },
                singleLine = true,
                isError = state.draft.nationalId.isNotEmpty() && !state.isNationalIdValid,
                supportingText = {
                    if (state.draft.nationalId.isNotEmpty() && !state.isNationalIdValid) {
                        Text("Enter 6â€“20 characters", color = MaterialTheme.colorScheme.error)
                    }
                },
                textStyle = TextStyle(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF2A2A2A),
                    unfocusedContainerColor = Color(0xFF2A2A2A),
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_phone), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            OutlinedTextField(
                value = state.draft.phoneNumber,
                onValueChange = onPhoneChange,
                placeholder = { Text("Enter phone number", color = Color.Gray.copy(alpha = 0.5f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = state.draft.phoneNumber.isNotEmpty() && !state.isPhoneValid,
                supportingText = {
                    if (state.draft.phoneNumber.isNotEmpty() && !state.isPhoneValid) {
                        Text("Enter a valid phone number", color = MaterialTheme.colorScheme.error)
                    }
                },
                textStyle = TextStyle(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(70.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF2A2A2A),
                    unfocusedContainerColor = Color(0xFF2A2A2A),
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun KycStepPreview() {
    SecurePayAgentTheme {
        KycStep(
            state = EnrollmentUiState(draft = EnrollmentDraft(customerName = "John Doe", nationalId = "12345678", phoneNumber = "0712345678")),
            onNameChange = {},
            onNationalIdChange = {},
            onPhoneChange = {}
        )
    }
}
