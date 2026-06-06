package com.securepay.customer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.securepay.customer.admin.LockEnforcer
import com.securepay.customer.data.model.LoanState
import com.securepay.customer.ui.theme.ErrorContainerCrimson
import com.securepay.customer.ui.theme.ErrorCrimson
import com.securepay.customer.ui.theme.OnBackgroundText
import java.text.NumberFormat
import java.util.Locale

private fun formatMoney(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return "KES " + nf.format(amount)
}

/**
 * Full-screen, crimson-tinted lock screen shown when the device status is
 * LOCKED (deadline passed). It blocks back navigation, explains the overdue
 * state, shows the outstanding balance, and offers an emergency-dial action
 * plus a grace-window request.
 *
 * On entry it engages the DPC via [LockEnforcer] (force-lock + debugging
 * restriction). These calls degrade gracefully when SecurePay is not the
 * provisioned device owner.
 */
@Composable
fun LockOverlayScreen(
    loan: LoanState,
    onRequestGrace: () -> Unit,
    onEmergencyCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Consume the back press so the customer cannot dismiss the lock screen.
    BackHandler(enabled = true) { /* swallow — lock screen is non-dismissible */ }

    // Engage real device-policy enforcement on entry. This is where the DPC
    // applies the financing lock: DevicePolicyManager.lockNow() (force-lock)
    // and addUserRestriction(DISALLOW_DEBUGGING_FEATURES) to prevent bypass.
    LaunchedEffect(Unit) {
        val enforcer = LockEnforcer(context)
        // setKeyguardDisabledFeatures / lockNow() would also be invoked here on
        // a provisioned device. Guarded internally; no-ops if not device owner.
        enforcer.restrictDebugging()
        enforcer.lockDevice()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ErrorContainerCrimson
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DEVICE LOCKED",
                style = MaterialTheme.typography.headlineLarge,
                color = OnBackgroundText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Your installment payment is overdue. To comply with " +
                    "your financing agreement, this device has been locked " +
                    "until the outstanding balance is settled.",
                style = MaterialTheme.typography.bodyLarge,
                color = OnBackgroundText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            Text(
                text = "Outstanding balance",
                style = MaterialTheme.typography.labelLarge,
                color = OnBackgroundText,
                textAlign = TextAlign.Center
            )
            Text(
                text = formatMoney(loan.remainingBalance),
                style = MaterialTheme.typography.displayLarge,
                color = OnBackgroundText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Device: ${loan.deviceModel}\nIMEI: ${loan.imei}",
                style = MaterialTheme.typography.bodyLarge,
                color = OnBackgroundText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))

            // Emergency calls remain available even while locked (ACTION_DIAL
            // to 112 — the GSM emergency number — never requires CALL_PHONE).
            Button(
                onClick = {
                    onEmergencyCall()
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:112")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorCrimson,
                    contentColor = OnBackgroundText
                )
            ) {
                Text("Emergency Calls")
            }
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onRequestGrace,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = OnBackgroundText
                )
            ) {
                Text("Request 5-Minute Grace Window")
            }
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Need help? Contact SecurePay support to make a payment " +
                    "and restore full access to your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFD7D7),
                textAlign = TextAlign.Center
            )
        }
    }
}
