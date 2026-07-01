package com.touchbase.user.ui.lock


import com.touchbase.user.ui.components.ButtonText
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.formatCentsAsCurrency
import com.touchbase.user.ui.DeviceUiState
import com.touchbase.user.ui.theme.Charcoal
import com.touchbase.user.ui.theme.Crimson
import com.touchbase.user.ui.theme.CrimsonDim
import com.touchbase.user.ui.theme.TextPrimary
import com.touchbase.user.ui.theme.TextSecondary

private const val EMERGENCY_DIAL_NUMBER = "112"

@Composable
fun LockOverlayScreen(
    state: DeviceUiState,
    onRequestGrace: () -> Unit
) {
    BackHandler(enabled = true) { /* intentionally consumed */ }

    val context = LocalContext.current
    val account = state.account

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(listOf(Charcoal, CrimsonDim.copy(alpha = 0.45f)))
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Crimson.copy(alpha = 0.16f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Device locked",
                    tint = Crimson,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Device Locked",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your financing payment is overdue. Make a payment to " +
                    "immediately restore full access to your device.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            if (account != null) {
                Spacer(Modifier.height(20.dp))
                LockedAccountSummary(account)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = state.remaining.format(),
                color = Crimson,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            )

            Spacer(Modifier.height(32.dp))
            OutlinedButton(
                onClick = {
                    val dial = Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:$EMERGENCY_DIAL_NUMBER")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(dial) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                ButtonText("Emergency Calls", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onRequestGrace,
                enabled = !state.isRequestingGrace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Crimson,
                    contentColor = TextPrimary
                )
            ) {
                if (state.isRequestingGrace) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TextPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Schedule, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    ButtonText("Sync Now", fontWeight = FontWeight.SemiBold)
                }
            }

            state.message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = TextSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun LockedAccountSummary(account: LoanAccount) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Charcoal.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SummaryRow("Account Holder", account.customerName)
        SummaryRow("Device", account.deviceModel)
        SummaryRow("Account", account.id)
        SummaryRow("Outstanding", formatCentsAsCurrency(account.remainingBalanceCents, account.currencyCode))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}
