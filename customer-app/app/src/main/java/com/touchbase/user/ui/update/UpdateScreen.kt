package com.touchbase.user.ui.update

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.touchbase.user.BuildConfig
import com.touchbase.user.data.model.AppUpdateResponse
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.ui.theme.Amber
import com.touchbase.user.ui.theme.Charcoal
import com.touchbase.user.ui.theme.CharcoalElevated
import com.touchbase.user.ui.theme.Emerald
import com.touchbase.user.ui.theme.TextPrimary
import com.touchbase.user.ui.theme.TextSecondary
import com.touchbase.user.worker.AppUpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class UpdatePhase { IDLE, CHECKING, DOWNLOADING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    repository: DeviceRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(UpdatePhase.IDLE) }
    var update by remember { mutableStateOf<AppUpdateResponse?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun checkUpdate(autoInstall: Boolean = false) {
        if (phase != UpdatePhase.IDLE) return
        phase = UpdatePhase.CHECKING
        message = null
        scope.launch {
            val result = repository.checkForAppUpdate()
            result.fold(
                onSuccess = { response ->
                    update = response
                    if (response.available) {
                        message = "Version ${response.versionName} (${response.versionCode}) is available."
                        if (autoInstall) {
                            phase = UpdatePhase.IDLE
                            phase = UpdatePhase.DOWNLOADING
                            val ok = withContext(Dispatchers.IO) {
                                AppUpdateInstaller.downloadVerifyAndInstall(context.applicationContext, response.url, response.sha256Base64)
                            }
                            message = if (ok) "Update package verified. Android is installing it now." else "Update failed. Check network, APK URL, and checksum."
                            phase = UpdatePhase.IDLE
                            return@launch
                        }
                    } else {
                        message = "You are already on the latest version."
                    }
                },
                onFailure = { message = it.message ?: "Update check failed" }
            )
            phase = UpdatePhase.IDLE
        }
    }

    LaunchedEffect(Unit) { checkUpdate(autoInstall = false) }

    Scaffold(
        containerColor = Charcoal,
        topBar = {
            TopAppBar(
                title = { Text("App Updates", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Charcoal)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(76.dp).background(Emerald.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = Emerald, modifier = Modifier.size(42.dp))
                    }
                    Text("Manual Update Check", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "Checks your dealer server for a newer TB User APK. If an update is available, the app downloads it, verifies its SHA-256 checksum, then asks Android Package Installer to install it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    VersionRow("Installed", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    update?.let { VersionRow("Latest on server", "${it.versionName} (${it.versionCode})") }
                }
            }

            message?.let {
                Text(it, color = if (update?.available == true) Amber else Emerald, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            if (phase != UpdatePhase.IDLE) {
                CircularProgressIndicator(color = Emerald)
                Text(if (phase == UpdatePhase.CHECKING) "Checking server…" else "Downloading and verifying APK…", color = TextSecondary)
            }

            Button(
                onClick = { checkUpdate(autoInstall = false) },
                enabled = phase == UpdatePhase.IDLE,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color(0xFF07130F))
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Check Again", fontWeight = FontWeight.Bold)
            }

            val available = update?.available == true && !update?.url.isNullOrBlank() && !update?.sha256Base64.isNullOrBlank()
            OutlinedButton(
                onClick = {
                    val latest = update
                    if (latest != null) {
                        scope.launch {
                            phase = UpdatePhase.DOWNLOADING
                            message = null
                            val ok = withContext(Dispatchers.IO) {
                                AppUpdateInstaller.downloadVerifyAndInstall(context.applicationContext, latest.url, latest.sha256Base64)
                            }
                            message = if (ok) "Update package verified. Android is installing it now." else "Update failed. Check network, APK URL, and checksum."
                            phase = UpdatePhase.IDLE
                        }
                    }
                },
                enabled = phase == UpdatePhase.IDLE && available,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Icon(if (available) Icons.Filled.Download else Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (available) "Download and Install" else "No Update Available", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
