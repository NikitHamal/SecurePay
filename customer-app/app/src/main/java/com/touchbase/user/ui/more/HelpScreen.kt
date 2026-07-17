package com.touchbase.user.ui.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.touchbase.user.BuildConfig
import com.touchbase.user.ui.theme.Gold
import com.touchbase.user.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun open(intent: Intent, configured: Boolean) {
        if (!configured) {
            scope.launch { snackbar.showSnackbar("Support contact has not been configured yet") }
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { scope.launch { snackbar.showSnackbar("No compatible app is available") } }
    }

    val whatsapp = BuildConfig.SUPPORT_WHATSAPP.filter(Char::isDigit)
    val phone = BuildConfig.SUPPORT_PHONE.trim()
    val email = BuildConfig.SUPPORT_EMAIL.trim()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Contact us", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Contact us", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ContactRow(Icons.Filled.Chat, "Chat", "Chat directly with our support agent") {
                open(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$whatsapp")), whatsapp.isNotBlank())
            }
            ContactRow(Icons.Filled.Forum, "WhatsApp", "Chat with us on WhatsApp") {
                open(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$whatsapp")), whatsapp.isNotBlank())
            }
            ContactRow(Icons.Filled.Call, "Call", "Call our support team directly") {
                open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")), phone.isNotBlank())
            }
            ContactRow(Icons.Filled.Email, "Email", "Send an email to our support team") {
                open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")), email.isNotBlank())
            }
        }
    }
}

@Composable
private fun ContactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary)
            }
            Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(28.dp))
        }
    }
}
