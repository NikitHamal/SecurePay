package com.securepay.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securepay.agent.data.model.EnrollmentData
import java.util.Locale

/**
 * Read-only finance summary derived from the current [EnrollmentData] draft.
 * Highlights the financed principal and the derived daily installment.
 */
@Composable
fun SummaryCard(
    data: EnrollmentData,
    planLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Finance Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SummaryRow(label = "Total loan amount", value = formatCurrency(data.totalLoanAmount))
            SummaryRow(label = "Down payment", value = formatCurrency(data.downPayment))

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)
            )

            SummaryRow(
                label = "Financed amount",
                value = formatCurrency(data.financedAmount),
                emphasize = true
            )
            SummaryRow(label = "Plan term", value = planLabel)
            SummaryRow(
                label = "Daily installment",
                value = formatCurrency(data.dailyInstallment),
                emphasize = true
            )
            SummaryRow(label = "Schedule length", value = "${data.totalDays} days")
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    emphasize: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatCurrency(amount: Double): String =
    "$" + String.format(Locale.US, "%,.2f", amount)
