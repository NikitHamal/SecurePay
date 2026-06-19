package com.touchbase.agent.ui.provisioning

import android.app.Activity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touchbase.agent.ui.theme.Amber
import com.touchbase.agent.ui.theme.DeepCharcoal
import com.touchbase.agent.ui.theme.EmeraldGreen
import com.touchbase.agent.ui.theme.ElevatedSurface
import com.touchbase.agent.ui.theme.OnDarkSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningScreen(
    viewModel: ProvisioningViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        containerColor = DeepCharcoal,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Provision Device", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepCharcoal)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.qrPayload.isBlank()) {
                QrFormSection(state, viewModel)
            } else {
                QrDisplaySection(state, onDone)
            }
        }
    }
}

@Composable
private fun QrFormSection(
    state: ProvisioningUiState,
    viewModel: ProvisioningViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Generate a provisioning QR",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "This installs TB User on the customer's phone as a Device Owner and binds it to their account. Saved Wi-Fi is stored encrypted on this agent device only.",
                color = OnDarkSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = state.imei,
                onValueChange = viewModel::updateImei,
                label = { Text("IMEI (15 digits)") },
                singleLine = true,
                isError = state.imei.length != 15 && state.imei.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Wifi, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Shop Wi-Fi (recommended)", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }

            OutlinedTextField(
                value = state.wifiSsid,
                onValueChange = viewModel::updateWifiSsid,
                label = { Text("Wi-Fi network name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = state.wifiPassword,
                onValueChange = viewModel::updateWifiPassword,
                label = { Text("Wi-Fi password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.rememberWifi,
                    onCheckedChange = viewModel::updateRememberWifi
                )
                Spacer(Modifier.width(8.dp))
                Text("Remember this Wi-Fi", color = OnDarkSecondary, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = viewModel::generateQr,
                enabled = state.imei.length == 15 && !state.isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate QR", fontWeight = FontWeight.SemiBold, color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun QrDisplaySection(
    state: ProvisioningUiState,
    onDone: () -> Unit
) {
    val activated = state.stage == ProvisioningStage.ACTIVATED

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QrCode(content = state.qrPayload, size = 300.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "Activation Code",
                color = DeepCharcoal,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = state.activationCode.chunked(1).joinToString(" "),
                color = DeepCharcoal,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "On the customer's phone:",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            StepRow("1", "Factory-reset the phone (no Google account).")
            StepRow("2", "On the welcome screen, tap 6 times.")
            StepRow("3", "Scan this QR when the scanner opens.")
            StepRow("4", "When TB User opens, enter the code above.")
            Text(
                state.securityText,
                color = if (state.securityText.startsWith("EFRP enabled")) EmeraldGreen else Amber,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (activated) Icons.Filled.CheckCircle else Icons.Filled.QrCode2,
            contentDescription = null,
            tint = if (activated) EmeraldGreen else Amber,
            modifier = Modifier.size(20.dp)
        )
        Text(
            state.statusText,
            color = if (activated) EmeraldGreen else Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onDone,
        enabled = activated,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
    ) {
        Text("Done", fontWeight = FontWeight.SemiBold, color = Color.Black)
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = EmeraldGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = OnDarkSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
    }
}
