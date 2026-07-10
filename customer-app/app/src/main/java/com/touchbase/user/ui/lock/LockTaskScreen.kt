package com.touchbase.user.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.formatCentsAsCurrency
import com.touchbase.user.domain.RemainingTime

private val LockBackground = Color(0xFF090B10)
private val LockSurface = Color(0xE61A1D24)
private val LockText = Color(0xFFF8FAFC)
private val LockMuted = Color(0xFFB6C0CC)
private val LockDanger = Color(0xFFFF4D32)
private val LockNetwork = Color(0xFF33D6C4)

data class LockTaskUiState(
    val account: LoanAccount? = null,
    val remaining: RemainingTime = RemainingTime(0L),
    val isSyncing: Boolean = false,
    val message: String? = null
)

@Composable
fun LockTaskScreen(
    state: LockTaskUiState,
    onOpenInternet: () -> Unit,
    onEmergencyCall: () -> Unit,
    onSync: () -> Unit
) {
    BackHandler(enabled = true) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LockBackground, Color(0xFF151019), Color(0xFF2B0D0B))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(LockDanger.copy(alpha = 0.16f), RoundedCornerShape(46.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Device locked",
                    tint = LockDanger,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "Device Locked",
                color = LockText,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your financing payment is overdue. Connect to the internet, make a payment, then sync your account.",
                color = LockMuted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            state.account?.let {
                Spacer(Modifier.height(20.dp))
                LockedAccountSummary(it)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "TIME UNTIL NEXT CHECK",
                color = LockMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.remaining.format(),
                color = LockDanger,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 29.sp
            )

            Spacer(Modifier.height(26.dp))
            OutlinedButton(
                onClick = onOpenInternet,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LockNetwork),
                border = BorderStroke(1.dp, LockNetwork.copy(alpha = 0.65f))
            ) {
                Icon(Icons.Filled.Wifi, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Wi-Fi & mobile data", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onEmergencyCall,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LockText)
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Emergency call", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSync,
                enabled = !state.isSyncing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LockDanger,
                    contentColor = Color.White,
                    disabledContainerColor = LockDanger.copy(alpha = 0.45f)
                )
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Sync payment status", fontWeight = FontWeight.Bold)
                }
            }

            state.message?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = LockMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun LockedAccountSummary(account: LoanAccount) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LockSurface, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        SummaryRow("Account holder", account.customerName)
        SummaryRow("Device", account.deviceModel)
        SummaryRow("Account", account.id)
        SummaryRow("Outstanding", formatCentsAsCurrency(account.remainingBalanceCents, account.currencyCode), LockDanger)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = LockText) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = LockMuted, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            color = valueColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
