package com.touchbase.user.ui.account

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.ui.components.CustomerBottomBar
import com.touchbase.user.ui.theme.Charcoal
import com.touchbase.user.ui.theme.CharcoalElevated
import com.touchbase.user.ui.theme.Gold
import com.touchbase.user.ui.theme.TextPrimary
import com.touchbase.user.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    account: LoanAccount?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onPayments: () -> Unit,
    onMore: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val viewModel: AccountViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(uiState.passwordChanged) {
        if (uiState.passwordChanged) {
            Toast.makeText(context, "Password changed successfully!", Toast.LENGTH_LONG).show()
            viewModel.resetPasswordChanged()
        }
    }

    Scaffold(
        containerColor = Charcoal,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Account", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Charcoal)
            )
        },
        bottomBar = {
            CustomerBottomBar(
                selected = "account",
                onHome = onHome,
                onPayments = onPayments,
                onMore = onMore,
                onAccount = {}
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Account Information Card
            AccountInfoCard(account)
            
            // Change Password Card
            ChangePasswordCard(
                currentPassword = uiState.currentPassword,
                onCurrentPasswordChange = { viewModel.updateCurrentPassword(it) },
                newPassword = uiState.newPassword,
                onNewPasswordChange = { viewModel.updateNewPassword(it) },
                confirmPassword = uiState.confirmPassword,
                onConfirmPasswordChange = { viewModel.updateConfirmPassword(it) },
                showCurrentPassword = showCurrentPassword,
                onToggleCurrentPassword = { showCurrentPassword = !showCurrentPassword },
                showNewPassword = showNewPassword,
                onToggleNewPassword = { showNewPassword = !showNewPassword },
                showConfirmPassword = showConfirmPassword,
                onToggleConfirmPassword = { showConfirmPassword = !showConfirmPassword },
                isLoading = uiState.isLoading,
                error = uiState.error,
                onChangePassword = {
                    account?.let { acc ->
                        viewModel.changePassword(acc.id, acc.phoneNumber)
                    }
                }
            )
        }
    }
}

@Composable
private fun AccountInfoCard(account: LoanAccount?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Account Information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            
            InfoRow(Icons.Filled.Person, "Account Number", account?.id?.takeLast(8) ?: "N/A")
            InfoRow(Icons.Filled.Phone, "Phone", account?.phoneNumber ?: "N/A")
            InfoRow(Icons.Filled.Lock, "Status", if (account?.isStolen == true) "Stolen" else "Active")
            
            account?.let { acc ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Loan Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    InfoRow(Icons.Filled.Lock, "Plan", acc.planName)
                    InfoRow(Icons.Filled.Lock, "Daily Rate", "GH₵${"%.2f".format(acc.dailyRateCents / 100.0)}")
                    InfoRow(Icons.Filled.Lock, "Remaining", "GH₵${"%.2f".format(acc.remainingBalanceCents / 100.0)}")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: android.graphics.drawable.Icon, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
    }
}

@Composable
private fun ChangePasswordCard(
    currentPassword: String,
    onCurrentPasswordChange: (String) -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    showCurrentPassword: Boolean,
    onToggleCurrentPassword: () -> Unit,
    showNewPassword: Boolean,
    onToggleNewPassword: () -> Unit,
    showConfirmPassword: Boolean,
    onToggleConfirmPassword: () -> Unit,
    isLoading: Boolean,
    error: String?,
    onChangePassword: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalElevated)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Change Password", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            
            val textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = Color(0xFF262629),
                cursorColor = Gold,
                focusedLabelColor = Gold,
                unfocusedLabelColor = TextSecondary
            )
            
            // Current Password
            OutlinedTextField(
                value = currentPassword,
                onValueChange = onCurrentPasswordChange,
                label = { Text("Current Password") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleCurrentPassword) {
                        Icon(
                            if (showCurrentPassword) Icons.Filled.Check else Icons.Filled.Lock,
                            contentDescription = if (showCurrentPassword) "Hide" else "Show",
                            tint = Gold
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            
            // New Password
            OutlinedTextField(
                value = newPassword,
                onValueChange = onNewPasswordChange,
                label = { Text("New Password") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleNewPassword) {
                        Icon(
                            if (showNewPassword) Icons.Filled.Check else Icons.Filled.Lock,
                            contentDescription = if (showNewPassword) "Hide" else "Show",
                            tint = Gold
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Confirm Password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Confirm New Password") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleConfirmPassword) {
                        Icon(
                            if (showConfirmPassword) Icons.Filled.Check else Icons.Filled.Lock,
                            contentDescription = if (showConfirmPassword) "Hide" else "Show",
                            tint = Gold
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Error message
            error?.let {
                Text(
                    it,
                    color = Color(0xFFDC2626),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Change Password Button
            Button(
                onClick = onChangePassword,
                enabled = !isLoading && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0B0B0C))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF0B0B0C))
                } else {
                    Text("Change Password", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Helper to use Android icons
@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
    }
}
