package com.securepay.agent.ui.screens.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.securepay.agent.data.model.EnrollmentData

/**
 * Step 1 — KYC capture. Collects the customer's identity details with inline
 * validation surfaced via supporting text.
 */
@Composable
fun KycStep(
    data: EnrollmentData,
    onUpdate: (customerName: String, nationalId: String, phoneNumber: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nameError = data.customerName.isNotEmpty() && data.customerName.trim().length < 2
    val idError = data.nationalId.isNotEmpty() && data.nationalId.trim().length < 6
    val phoneDigits = data.phoneNumber.filter { it.isDigit() }
    val phoneError = data.phoneNumber.isNotEmpty() && phoneDigits.length < 9

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Customer details",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Capture the new customer's KYC information.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = data.customerName,
            onValueChange = { onUpdate(it, data.nationalId, data.phoneNumber) },
            label = { Text("Full name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            isError = nameError,
            supportingText = {
                if (nameError) Text("Enter the customer's full name.")
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType = KeyboardType.Text
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = data.nationalId,
            onValueChange = { onUpdate(data.customerName, it, data.phoneNumber) },
            label = { Text("National ID") },
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
            singleLine = true,
            isError = idError,
            supportingText = {
                if (idError) Text("National ID must be at least 6 characters.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = data.phoneNumber,
            onValueChange = { onUpdate(data.customerName, data.nationalId, it) },
            label = { Text("Phone number") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            singleLine = true,
            isError = phoneError,
            supportingText = {
                if (phoneError) Text("Enter a valid phone number (min 9 digits).")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
