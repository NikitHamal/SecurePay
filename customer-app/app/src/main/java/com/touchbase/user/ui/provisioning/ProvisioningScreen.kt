package com.touchbase.user.ui.provisioning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.admin.ProvisioningManager
import com.touchbase.user.admin.ProvisioningState
import com.touchbase.user.admin.SecurityChecker
import com.touchbase.user.ui.theme.Amber
import com.touchbase.user.ui.theme.Crimson
import com.touchbase.user.ui.theme.Emerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningScreen(
    provisioningManager: ProvisioningManager,
    policyController: DevicePolicyController,
    appContext: android.content.Context,
    onProvisioningComplete: () -> Unit,
    onLaunchProvisioning: (android.content.Intent) -> Unit,
    onLaunchEnableAdmin: (android.content.Intent) -> Unit
) {
    val viewModel = remember { ProvisioningViewModel(provisioningManager, policyController, appContext) }
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.provisioningState) {
        if (state.provisioningState == ProvisioningState.DEVICE_OWNER && !state.securityReport.hasWarnings) {
            onProvisioningComplete()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Device Setup", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = Emerald,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "TB User Device Setup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "This phone must be provisioned as Device Owner from the Android welcome screen. Device Admin mode is blocked because it cannot stop factory reset, uninstall controls, or full loan security policies.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            ProvisioningStatusCard(state = state)

            SecurityWarningsCard(securityReport = state.securityReport)

            Spacer(Modifier.height(8.dp))

            ProvisioningActions(
                state = state,
                onRequestDeviceOwner = {
                    val intent = viewModel.getDeviceOwnerIntent()
                    if (intent != null) {
                        onLaunchProvisioning(intent)
                    }
                },
                onRunSecurityCheck = { viewModel.runSecurityCheck() }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProvisioningStatusCard(state: ProvisioningUiState) {
    val (icon, color, label) = when (state.provisioningState) {
        ProvisioningState.DEVICE_OWNER -> Triple(
            Icons.Filled.CheckCircle, Emerald, "Device Owner (Full Control)"
        )
        ProvisioningState.ADMIN_ACTIVE -> Triple(
            Icons.Filled.Warning, Crimson, "Device Admin only — re-provision required"
        )
        ProvisioningState.PROVISIONING_IN_PROGRESS -> Triple(
            Icons.Filled.AdminPanelSettings, Amber, "Provisioning in Progress..."
        )
        ProvisioningState.NOT_PROVISIONED -> Triple(
            Icons.Filled.Warning, Crimson, "Not Provisioned"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "Provisioning Status",
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (state.provisioningState == ProvisioningState.ADMIN_ACTIVE) {
                Text(
                    text = "Manual Device Admin is not production safe. Factory reset and uninstall controls are only enforceable after true Device Owner QR provisioning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            if (state.provisioningState == ProvisioningState.NOT_PROVISIONED) {
                Text(
                    text = "Factory reset the phone, stay on the welcome screen, tap 6 times, then scan the dealer QR.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SecurityWarningsCard(securityReport: SecurityChecker.SecurityReport) {
    if (!securityReport.hasWarnings) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (securityReport.isRooted) Crimson.copy(alpha = 0.1f) else Amber.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = if (securityReport.isRooted) Crimson else Amber,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = if (securityReport.isRooted) "Security Risk Detected" else "Security Warnings",
                style = MaterialTheme.typography.titleSmall,
                color = if (securityReport.isRooted) Crimson else Amber,
                fontWeight = FontWeight.SemiBold
            )

            if (securityReport.isRooted) {
                SecurityWarningRow("Rooted device detected — financing terms may not be enforceable")
            }
            if (securityReport.isEmulator) {
                SecurityWarningRow("Emulator environment detected")
            }
            if (securityReport.isDebuggable) {
                SecurityWarningRow("Debug build — not suitable for production")
            }
            if (securityReport.isTampered) {
                SecurityWarningRow("App integrity check failed")
            }
        }
    }
}

@Composable
private fun SecurityWarningRow(text: String) {
    Text(
        text = "\u2022 $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun ProvisioningActions(
    state: ProvisioningUiState,
    onRequestDeviceOwner: () -> Unit,
    onRunSecurityCheck: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.provisioningState == ProvisioningState.NOT_PROVISIONED) {
            OutlinedButton(
                onClick = onRequestDeviceOwner,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Security, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Open legacy Device Owner setup", fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "On Android 12+ this button is intentionally unavailable. Use the dealer QR from Setup Wizard instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.provisioningState == ProvisioningState.ADMIN_ACTIVE) {
            Text(
                text = "This phone is only Device Admin. Do not activate a loan on it. Factory reset and scan a fresh dealer QR to become Device Owner.",
                style = MaterialTheme.typography.bodyMedium,
                color = Crimson,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onRunSecurityCheck,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            if (state.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Run Security Check", fontWeight = FontWeight.Medium)
            }
        }
    }
}
