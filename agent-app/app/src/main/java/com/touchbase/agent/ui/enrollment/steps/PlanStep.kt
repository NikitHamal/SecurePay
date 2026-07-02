package com.touchbase.agent.ui.enrollment.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.agent.R
import com.touchbase.agent.data.model.Plan
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.ui.enrollment.EnrollmentUiState
import com.touchbase.agent.ui.theme.SecurePayAgentTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanStep(
    state: EnrollmentUiState,
    onSelectPlan: (Plan?) -> Unit,
    onDailyRateChange: (String) -> Unit,
    onTotalAmountChange: (String) -> Unit,
    onTermDaysChange: (String) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlan = state.selectedPlan

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.label_plan), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedPlan?.name ?: stringResource(R.string.label_plan_custom),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(360.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.label_plan_custom), fontWeight = if (selectedPlan == null) FontWeight.Bold else FontWeight.Normal)
                        },
                        onClick = {
                            onSelectPlan(null)
                            expanded = false
                        }
                    )
                    state.availablePlans.forEach { plan ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(plan.name, style = MaterialTheme.typography.titleMedium)
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
            Text(stringResource(R.string.summary_daily_rate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.dailyRateInput,
                onValueChange = onDailyRateChange,
                placeholder = { Text("0.00", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.dailyRateInput.isNotEmpty() && !state.isDailyRateValid,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.summary_total_loan), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.totalAmountInput,
                onValueChange = onTotalAmountChange,
                placeholder = { Text("0.00", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.totalAmountInput.isNotEmpty() && !state.isTotalAmountValid,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(360.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.summary_term), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.termDaysInput,
                    onValueChange = onTermDaysChange,
                    placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.termDaysInput.isNotEmpty() && !state.isTermDaysValid,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(360.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.label_down_payment), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.downPaymentInput,
                    onValueChange = onDownPaymentChange,
                    placeholder = { Text("0.00", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.downPaymentInput.isNotEmpty() && !state.isDownPaymentValid,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(360.dp)
                )
            }
        }

        if (selectedPlan != null || (state.dailyRateInput.isNotBlank() || state.totalAmountInput.isNotBlank())) {
            SummaryCard(
                planName = selectedPlan?.name ?: stringResource(R.string.label_plan_custom),
                totalAmount = state.totalAmountInput.toDoubleOrNull()?.let { (it * 100).toInt() }
                    ?: selectedPlan?.totalAmount ?: 0,
                dailyRate = state.dailyRateInput.toDoubleOrNull()?.let { (it * 100).toInt() }
                    ?: selectedPlan?.dailyRate ?: 0,
                termDays = state.termDaysInput.toIntOrNull()
                    ?: selectedPlan?.termDays ?: 0
            )
        }
    }
}

@Composable
private fun SummaryCard(
    planName: String,
    totalAmount: Int,
    dailyRate: Int,
    termDays: Int,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                text = planName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SummaryRow(
                label = stringResource(R.string.summary_total_loan),
                value = formatAmount(totalAmount)
            )
            SummaryRow(
                label = stringResource(R.string.summary_daily_rate),
                value = formatAmount(dailyRate)
            )
            SummaryRow(
                label = stringResource(R.string.summary_term),
                value = "${termDays} days"
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
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PlanStepPreview() {
    val previewPlans = listOf(
        Plan("p1", "Basic Solar", 15000, 300, 50, 1500)
    )
    SecurePayAgentTheme {
        PlanStep(
            state = EnrollmentUiState(
                availablePlans = previewPlans,
                dailyRateInput = "50",
                totalAmountInput = "15000",
                termDaysInput = "300",
                downPaymentInput = "2000"
            ),
            onSelectPlan = {},
            onDailyRateChange = {},
            onTotalAmountChange = {},
            onTermDaysChange = {},
            onDownPaymentChange = {}
        )
    }
}
