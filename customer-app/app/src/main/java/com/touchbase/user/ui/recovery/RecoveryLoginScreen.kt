package com.touchbase.user.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.ui.theme.Emerald
import com.touchbase.user.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun RecoveryLoginScreen(
    repository: DeviceRepository,
    expectedImei: String?,
    onRecovered: () -> Unit,
    onUseActivationCode: () -> Unit,
    onHelp: () -> Unit
) {
    var accountNumber by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val cleanImei = expectedImei.orEmpty().filter(Char::isDigit)

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.LockOpen, contentDescription = null, tint = Emerald)
            Spacer(Modifier.height(16.dp))
            Text("TB USER", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
            Text(
                "Log in to restore this financed phone after setup or an authorized reset.",
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))

            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it.filter(Char::isDigit).take(15) },
                label = { Text("Account Number") },
                supportingText = { Text("Use the phone number registered by your dealer") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Emerald)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                label = { Text("Password / PIN") },
                singleLine = true,
                visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                trailingIcon = {
                    IconButton(onClick = { pinVisible = !pinVisible }) {
                        Icon(if (pinVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Emerald)
            )

            if (cleanImei.length != 15) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "This installation is not linked to an inventory IMEI. Ask the dealer to provision this exact phone again.",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        repository.customerLogin(accountNumber, pin, cleanImei).fold(
                            onSuccess = { response ->
                                if (response.activated) onRecovered() else error = "Login could not restore the device"
                            },
                            onFailure = { error = it.message ?: "Login failed" }
                        )
                        loading = false
                    }
                },
                enabled = !loading && accountNumber.length >= 8 && pin.length >= 6 && cleanImei.length == 15,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald)
            ) {
                if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("LOGIN", fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = onUseActivationCode) { Text("Use dealer activation code") }
            TextButton(onClick = onHelp) { Text("Need support?") }
        }
    }
}
