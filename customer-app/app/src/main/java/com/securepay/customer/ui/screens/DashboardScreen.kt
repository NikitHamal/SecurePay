package com.securepay.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.securepay.customer.data.model.DeviceStatus
import com.securepay.customer.ui.components.BalanceRow
import com.securepay.customer.ui.components.StatusChip
import com.securepay.customer.ui.theme.BackgroundCharcoal
import com.securepay.customer.ui.theme.ErrorCrimson
import com.securepay.customer.ui.theme.OnBackgroundText
import com.securepay.customer.ui.theme.PrimaryEmerald
import com.securepay.customer.ui.theme.SecondaryText
import com.securepay.customer.ui.theme.SurfaceDark
import com.securepay.customer.ui.theme.SurfaceVariantDark
import com.securepay.customer.ui.theme.WarningAmber
import com.securepay.customer.ui.viewmodel.DashboardUiState
import java.text.NumberFormat
import java.util.Locale

/** Format a Double as a localized currency-style string. */
private fun formatMoney(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return "KES " + nf.format(amount)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onSimulatePayment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundCharcoal,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SecurePay",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundCharcoal,
                    titleContentColor = OnBackgroundText
                )
            )
        }
    ) { innerPadding ->
        if (state.isLoading || state.loan == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = PrimaryEmerald)
                Spacer(Modifier.height(16.dp))
                Text("Loading your financing details...", color = SecondaryText)
            }
            return@Scaffold
        }

        val loan = state.loan
        val statusColor = when (state.status) {
            DeviceStatus.ACTIVE -> PrimaryEmerald
            DeviceStatus.WARNING -> WarningAmber
            DeviceStatus.LOCKED -> ErrorCrimson
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Hi, ${loan.customerName}",
                style = MaterialTheme.typography.headlineLarge,
                color = OnBackgroundText
            )
            Text(
                text = loan.deviceModel,
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryText
            )
            Spacer(Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = SurfaceDark),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusChip(status = state.status)
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Time until next payment",
                        style = MaterialTheme.typography.labelLarge,
                        color = SecondaryText
                    )
                    Spacer(Modifier.height(4.dp))

                    // Large ticking countdown — updated every second by the VM.
                    Text(
                        text = state.remainingFormatted,
                        style = MaterialTheme.typography.displayLarge,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))

                    // Dynamic countdown progress: drains as the deadline nears.
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = statusColor,
                        trackColor = SurfaceVariantDark
                    )
                    Spacer(Modifier.height(20.dp))

                    HorizontalDivider(color = SurfaceVariantDark)
                    Spacer(Modifier.height(8.dp))

                    BalanceRow(
                        label = "Remaining balance",
                        value = formatMoney(loan.remainingBalance),
                        valueColor = OnBackgroundText
                    )
                    BalanceRow(
                        label = "Amount paid",
                        value = formatMoney(loan.amountPaid),
                        valueColor = PrimaryEmerald
                    )
                    BalanceRow(
                        label = "Total loan",
                        value = formatMoney(loan.totalLoanAmount)
                    )
                    BalanceRow(
                        label = "Daily installment",
                        value = formatMoney(loan.dailyInstallment)
                    )
                    BalanceRow(
                        label = "IMEI",
                        value = loan.imei
                    )

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = SurfaceVariantDark)
                    Spacer(Modifier.height(16.dp))

                    FilledTonalButton(
                        onClick = onSimulatePayment,
                        enabled = !state.isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.isProcessing) {
                                "Processing..."
                            } else {
                                "Simulate Payment Integration"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Repayment progress: " +
                    "${(loan.paymentProgress * 100).toInt()}% complete",
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryText,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { loan.paymentProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = PrimaryEmerald,
                trackColor = SurfaceVariantDark
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
