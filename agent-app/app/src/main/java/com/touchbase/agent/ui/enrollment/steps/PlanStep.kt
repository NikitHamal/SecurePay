package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.touchbase.agent.R
import com.touchbase.agent.data.model.Plan
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import androidx.compose.ui.tooling.preview.Preview
import com.touchbase.agent.ui.theme.SecurePayAgentTheme

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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_plan), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedPlan?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select financing plan", color = Color.Gray.copy(alpha = 0.5f), fontSize = 15.sp) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    textStyle = TextStyle(fontSize = 15.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .menuAnchor(),
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
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_down_payment), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            OutlinedTextField(
                value = state.downPaymentInput,
                onValueChange = onDownPaymentChange,
                placeholder = { Text("Enter down payment", color = Color.Gray.copy(alpha = 0.5f)) },
                singleLine = true,
                enabled = selectedPlan != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.downPaymentInput.isNotEmpty() && !state.isDownPaymentValid,
                supportingText = {
                    if (selectedPlan != null) {
                        if (state.downPaymentInput.isNotEmpty() && !state.isDownPaymentValid) {
                            Text("Must be between ${formatAmount(selectedPlan.minDownPayment)} and ${formatAmount(selectedPlan.totalAmount)}", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Min: ${formatAmount(selectedPlan.minDownPayment)}", color = Color.Gray)
                        }
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
                    cursorColor = Color(0xFF10B981),
                    disabledContainerColor = Color(0xFF2A2A2A).copy(alpha = 0.5f),
                    disabledTextColor = Color.Gray,
                    disabledBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }

        if (selectedPlan != null) {
            SummaryCard(plan = selectedPlan)
        }
    }
}

@Composable
private fun SummaryCard(plan: Plan, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
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
                color = Color.White
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
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PlanStepPreview() {
    val samplePlans = listOf(
        Plan("p1", "Basic Solar", 15000, 300, 50, 1500)
    )
    SecurePayAgentTheme {
        PlanStep(
            state = EnrollmentUiState(availablePlans = samplePlans, selectedPlan = samplePlans[0], downPaymentInput = "2000"),
            onSelectPlan = {},
            onDownPaymentChange = {}
        )
    }
}
