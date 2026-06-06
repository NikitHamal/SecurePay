package com.securepay.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.securepay.agent.R
import com.securepay.agent.data.model.Plan
import com.securepay.agent.data.model.formatAmount
import com.securepay.agent.ui.enrollment.EnrollmentUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanStep(
    state: EnrollmentUiState,
    onSelectPlan: (Plan) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlan = state.selectedPlan

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedPlan?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.label_plan)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                state.availablePlans.forEach { plan ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(plan.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatAmount(plan.totalAmount)} · ${plan.termDays} days",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onSelectPlan(plan)
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.downPaymentInput,
            onValueChange = onDownPaymentChange,
            label = { Text(stringResource(R.string.label_down_payment)) },
            singleLine = true,
            enabled = selectedPlan != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.downPaymentInput.isNotEmpty() && !state.isDownPaymentValid,
            supportingText = {
                if (selectedPlan != null) {
                    if (state.downPaymentInput.isNotEmpty() && !state.isDownPaymentValid) {
                        Text("Must be between ${formatAmount(selectedPlan.minDownPayment)} and ${formatAmount(selectedPlan.totalAmount)}")
                    } else {
                        Text("Min: ${formatAmount(selectedPlan.minDownPayment)}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedPlan != null) {
            SummaryCard(plan = selectedPlan)
        }
    }
}

@Composable
private fun SummaryCard(plan: Plan, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SummaryRow(
                label = stringResource(R.string.summary_total_loan),
                value = formatAmount(plan.totalAmount)
            )
            SummaryRow(
                label = stringResource(R.string.summary_daily_rate),
                value = formatAmount(plan.dailyRate)
            )
            SummaryRow(
                label = stringResource(R.string.summary_term),
                value = "${plan.termDays} days"
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}