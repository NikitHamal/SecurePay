package com.securepay.agent.ui.screens.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.securepay.agent.data.model.EnrollmentData
import com.securepay.agent.data.model.FinancePlan
import com.securepay.agent.ui.components.SummaryCard

/**
 * Step 3 — Plan & payment. Captures loan totals, lets the agent pick a finance
 * plan via an [ExposedDropdownMenuBox], previews the derived summary and submits
 * the enrollment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentStep(
    data: EnrollmentData,
    selectedPlan: FinancePlan,
    totalAmountInput: String,
    downPaymentInput: String,
    isSubmitting: Boolean,
    onAmountsChange: (total: String, down: String) -> Unit,
    onPlanSelected: (FinancePlan) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var planMenuExpanded by remember { mutableStateOf(false) }

    val totalError = totalAmountInput.isNotEmpty() && (data.totalLoanAmount <= 0.0)
    val downError = downPaymentInput.isNotEmpty() &&
        (data.downPayment < 0.0 || data.downPayment >= data.totalLoanAmount)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Finance plan",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Set the loan amounts and repayment term.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = totalAmountInput,
            onValueChange = { onAmountsChange(it, downPaymentInput) },
            label = { Text("Total loan amount") },
            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
            singleLine = true,
            isError = totalError,
            supportingText = {
                if (totalError) Text("Enter a total amount greater than zero.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = downPaymentInput,
            onValueChange = { onAmountsChange(totalAmountInput, it) },
            label = { Text("Down payment") },
            leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
            singleLine = true,
            isError = downError,
            supportingText = {
                if (downError) Text("Down payment must be less than the total amount.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = planMenuExpanded,
            onExpandedChange = { planMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedPlan.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Repayment plan") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = planMenuExpanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = planMenuExpanded,
                onDismissRequest = { planMenuExpanded = false }
            ) {
                FinancePlan.entries.forEach { plan ->
                    DropdownMenuItem(
                        text = { Text(plan.label) },
                        onClick = {
                            onPlanSelected(plan)
                            planMenuExpanded = false
                        }
                    )
                }
            }
        }

        SummaryCard(
            data = data,
            planLabel = selectedPlan.label
        )

        Button(
            onClick = onSubmit,
            enabled = !isSubmitting &&
                data.totalLoanAmount > 0.0 &&
                data.downPayment >= 0.0 &&
                data.downPayment < data.totalLoanAmount,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "  Submitting...",
                    style = MaterialTheme.typography.labelLarge
                )
            } else {
                Text(
                    text = "Submit Enrollment",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
