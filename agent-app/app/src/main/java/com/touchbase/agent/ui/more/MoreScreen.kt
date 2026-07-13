package com.touchbase.agent.ui.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.touchbase.agent.BuildConfig
import com.touchbase.agent.ui.components.SecurePayBottomNavBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToContact: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showLegal by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    if (showLegal) {
        AlertDialog(
            onDismissRequest = { showLegal = false },
            title = { Text("Legal") },
            text = {
                Text("TB Agent must be used only by authorized dealers. Customer identity, device, payment and location data must be handled according to the signed financing agreement and applicable privacy law.")
            },
            confirmButton = { TextButton(onClick = { showLegal = false }) { Text("Close") } }
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You will need your dealer credentials to sign in again.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Log out", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("More", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            SecurePayBottomNavBar(
                selectedTab = 4,
                onHomeClick = onNavigateToHome,
                onCustomersClick = onNavigateToCustomers,
                onInventoryClick = onNavigateToInventory,
                onLedgerClick = onNavigateToLedger,
                onMoreClick = {}
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Personal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            MoreRow(Icons.Filled.CreditCard, "Payment Details", "View customer collections and transactions", onNavigateToLedger)

            Spacer(Modifier.height(8.dp))
            Text("App version", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MoreRow(Icons.Filled.Info, "Version ${BuildConfig.VERSION_NAME}", "Build ${BuildConfig.VERSION_CODE}") {}

            Spacer(Modifier.height(8.dp))
            Text("General", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MoreRow(Icons.Filled.Feedback, "Give app Feedback", "Email the TB support team") {
                val email = BuildConfig.SUPPORT_EMAIL.trim()
                if (email.isBlank()) {
                    scope.launch { snackbar.showSnackbar("Support email has not been configured") }
                } else {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email?subject=TB%20Agent%20feedback")))
                    }.onFailure {
                        scope.launch { snackbar.showSnackbar("No email app is available") }
                    }
                }
            }
            MoreRow(Icons.Filled.Palette, "App theme", "System, light or dark", onNavigateToTheme)
            MoreRow(Icons.Filled.Gavel, "Legal", "Privacy and authorized-use notice") { showLegal = true }
            MoreRow(Icons.Filled.SupportAgent, "Contact us", "Chat, WhatsApp, call or email", onNavigateToContact)
            MoreRow(Icons.Filled.Logout, "Log out", "Sign out of this dealer account", tint = Color.Red) { confirmLogout = true }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(46.dp)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(11.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (tint == Color.Red) Color.Red else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
