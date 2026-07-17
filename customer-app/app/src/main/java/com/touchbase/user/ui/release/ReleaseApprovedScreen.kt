package com.touchbase.user.ui.release


import com.touchbase.user.ui.components.ButtonText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.formatCentsAsCurrency
import com.touchbase.user.ui.theme.CharcoalElevated
import com.touchbase.user.ui.theme.TextSecondary

@Composable
fun ReleaseApprovedScreen(
    account: LoanAccount?,
    isReleasing: Boolean,
    managementReleased: Boolean,
    onRemoveApp: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Loan complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The dealer has approved this phone for release. Device management is being removed so Touch Base can be uninstalled.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        if (account != null) {
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(account.deviceModel, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Paid: ${formatCentsAsCurrency(account.amountPaidCents, account.currencyCode)}", color = TextSecondary)
                    Text("Remaining: ${formatCentsAsCurrency(account.remainingBalanceCents, account.currencyCode)}", color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        if (isReleasing) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Removing device management…", color = TextSecondary)
        } else {
            Button(
                onClick = onRemoveApp,
                enabled = managementReleased,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                ButtonText(if (managementReleased) "Remove Touch Base" else "Management removal pending")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                ButtonText("Refresh")
            }
        }
    }
}
