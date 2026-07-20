package com.touchbase.user.ui.more

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.touchbase.user.BuildConfig
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.ui.components.CustomerBottomBar
import com.touchbase.user.ui.theme.Gold
import com.touchbase.user.ui.theme.TextSecondary
import com.touchbase.user.util.BatteryOptimizationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    account: LoanAccount?,
    onHome: () -> Unit,
    onPayments: () -> Unit,
    onHelp: () -> Unit,
    onCheckUpdates: () -> Unit,
    onAccount: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("More", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            CustomerBottomBar(
                selected = "more",
                onHome = onHome,
                onPayments = onPayments,
                onMore = {},
                onAccount = onAccount
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("Device")
            MoreRow(Icons.Filled.PhoneAndroid, "${Build.MANUFACTURER} ${Build.MODEL}", account?.imei.orEmpty()) {}
            MoreRow(Icons.Filled.SystemUpdate, "App version ${BuildConfig.VERSION_NAME}", "Check for managed updates", onCheckUpdates)

            Spacer(Modifier.height(12.dp))
            SectionTitle("Settings")
            MoreRow(Icons.Filled.Notifications, "Notifications", "Open Android notification settings") {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                runCatching { context.startActivity(intent) }
            }
            MoreRow(Icons.Filled.Language, "Language", "English") {}
            MoreRow(Icons.Filled.Info, "Battery reliability", "Open power settings only when needed") {
                BatteryOptimizationHelper.requestIfRegistered(context)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Account")
            MoreRow(Icons.Filled.Person, "My Account", "Change password and manage account", onAccount)

            Spacer(Modifier.height(12.dp))
            SectionTitle("Support")
            MoreRow(Icons.Filled.SupportAgent, "Help & contact us", "Chat, WhatsApp, call or email", onHelp)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun MoreRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(46.dp)) {
                Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.padding(11.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
        }
    }
}
