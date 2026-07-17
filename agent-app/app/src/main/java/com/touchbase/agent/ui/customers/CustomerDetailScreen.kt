package com.touchbase.agent.ui.customers


import com.touchbase.agent.ui.components.ButtonText
import com.touchbase.agent.ui.components.ImageViewerDialog
import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.touchbase.agent.data.model.Account
import com.touchbase.agent.data.model.AccountStatus
import com.touchbase.agent.data.model.CustomerCredentials
import com.touchbase.agent.data.model.formatAmount
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.isLight
import com.touchbase.agent.ui.enrollment.steps.KycPhotoSelector
import kotlinx.coroutines.launch

import com.touchbase.agent.data.model.UpdateAccountRequest
import com.touchbase.agent.ui.payments.AgentPayWithMoMoDialog
import androidx.compose.material.icons.filled.AccountBalanceWallet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    accountId: String,
    repository: SecurePayRepository?,
    onBack: () -> Unit,
    onProvisionDevice: (imei: String) -> Unit = {},
    onViewLiveLocation: (accountId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var account by remember { mutableStateOf<Account?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    var customerCredentials by remember { mutableStateOf<CustomerCredentials?>(null) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showMoMoDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editNationalId by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editDailyRate by remember { mutableStateOf("") }
    var editTotalLoan by remember { mutableStateOf("") }
    var editTermDays by remember { mutableStateOf("") }
    var editCustomerPhoto by remember { mutableStateOf<String?>(null) }
    var editNationalIdFront by remember { mutableStateOf<String?>(null) }
    var editNationalIdBack by remember { mutableStateOf<String?>(null) }
    var customerPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var idFrontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var idBackBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var verifyInProgress by remember { mutableStateOf(false) }
    var verifyMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    val view = LocalView.current

    val isDark = isSystemInDarkTheme()
    val dynamicBgColor = MaterialTheme.colorScheme.background

    if (!isPreview) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = dynamicBgColor.toArgb()
            window.navigationBarColor = dynamicBgColor.toArgb()
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = dynamicBgColor.isLight()
        }
    }

    fun loadAccount() {
        if (isPreview) return
        isLoading = true
        scope.launch {
            val result = repository?.getAccount(accountId) ?: run {
                isLoading = false
                return@launch
            }
            isLoading = false
            result.fold(
                onSuccess = {
                    error = null
                    account = it
                    editName = it.customerName
                    editNationalId = it.nationalId
                    editPhone = it.phoneNumber
                    editDailyRate = (it.dailyRate / 100.0).toString()
                    editTotalLoan = (it.totalLoanAmount / 100.0).toString()
                    editTermDays = it.termDays.toString()
                },
                onFailure = { error = it.message }
            )
        }
    }

    fun startEditing() {
        account?.let { acc ->
            editName = acc.customerName
            editNationalId = acc.nationalId
            editPhone = acc.phoneNumber
            editDailyRate = (acc.dailyRate / 100.0).toString()
            editTotalLoan = (acc.totalLoanAmount / 100.0).toString()
            editTermDays = acc.termDays.toString()
            isEditing = true
        }
    }

     fun cancelEditing() {
        isEditing = false
        editCustomerPhoto = null
        editNationalIdFront = null
        editNationalIdBack = null
    }

    fun saveEditing() {
        val acc = account ?: return
        isSaving = true
        scope.launch {
            val newDaily = editDailyRate.toDoubleOrNull()?.let { Math.round(it * 100.0).toInt() } ?: acc.dailyRate
            val newTotal = editTotalLoan.toDoubleOrNull()?.let { Math.round(it * 100.0).toInt() } ?: acc.totalLoanAmount
            val newTerm = editTermDays.toIntOrNull() ?: acc.termDays
            val request = UpdateAccountRequest(
                customerName = editName.trim().takeIf { it != acc.customerName },
                nationalId = editNationalId.trim().takeIf { it != acc.nationalId },
                phoneNumber = editPhone.trim().takeIf { it != acc.phoneNumber },
                dailyRate = newDaily.takeIf { it != acc.dailyRate },
                totalLoanAmount = newTotal.takeIf { it != acc.totalLoanAmount },
                termDays = newTerm.takeIf { it != acc.termDays },
                customerPhoto = editCustomerPhoto,
                nationalIdFront = editNationalIdFront,
                nationalIdBack = editNationalIdBack
            )
            val hasChanges = listOf(
                request.customerName, request.nationalId, request.phoneNumber,
                request.dailyRate, request.totalLoanAmount, request.termDays,
                request.customerPhoto, request.nationalIdFront, request.nationalIdBack
            ).any { it != null }
            if (hasChanges) {
                repository?.updateAccount(acc.id, request)
            }
            editCustomerPhoto = null
            editNationalIdFront = null
            editNationalIdBack = null
            isSaving = false
            isEditing = false
            loadAccount()
        }
    }

    LaunchedEffect(accountId) { loadAccount() }

    LaunchedEffect(account) {
        val acc = account ?: return@LaunchedEffect
        if (!acc.customerPhotoPath.isNullOrEmpty()) {
            scope.launch {
                repository?.getPhoto(acc.id, "photo")?.fold(
                    onSuccess = { customerPhotoBitmap = it },
                    onFailure = { /* ignore */ }
                )
            }
        } else {
            customerPhotoBitmap = null
        }
        if (!acc.nationalIdFrontPath.isNullOrEmpty()) {
            scope.launch {
                repository?.getPhoto(acc.id, "id_front")?.fold(
                    onSuccess = { idFrontBitmap = it },
                    onFailure = { /* ignore */ }
                )
            }
        } else {
            idFrontBitmap = null
        }
        if (!acc.nationalIdBackPath.isNullOrEmpty()) {
            scope.launch {
                repository?.getPhoto(acc.id, "id_back")?.fold(
                    onSuccess = { idBackBitmap = it },
                    onFailure = { /* ignore */ }
                )
            }
        } else {
            idBackBitmap = null
        }
    }

    customerCredentials?.let { credentials ->
        AlertDialog(
            onDismissRequest = { customerCredentials = null },
            title = { Text("Customer login credentials") },
            text = {
                Text(
                    "Account number: ${credentials.accountNumber}\n" +
                        "Temporary PIN: ${credentials.temporaryPin}\n\n" +
                        "Give these details only to the verified customer. Resetting the PIN invalidates the previous one."
                )
            },
            confirmButton = {
                TextButton(onClick = { customerCredentials = null }) { Text("Done") }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = dynamicBgColor,
        topBar = {
            TopAppBar(
                title = { Text(account?.customerName ?: "Account", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    if (account != null && !isEditing) {
                        IconButton(onClick = { startEditing() }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = dynamicBgColor
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        val acc = account
        if (acc == null || error != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error ?: "Account not found", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            actionMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            StatusBanner(status = acc.status, releaseApproved = acc.releaseApproved)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (customerPhotoBitmap != null) {
                    Image(
                        bitmap = customerPhotoBitmap!!.asImageBitmap(),
                        contentDescription = "Profile Photo",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { viewerBitmap = customerPhotoBitmap },
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(acc.customerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("ID: ${acc.id}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (isEditing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Edit Customer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))

                        EditField(label = "Name", value = editName, onValueChange = { editName = it })
                        EditField(label = "National ID", value = editNationalId, onValueChange = { editNationalId = it })
                        EditField(label = "Phone", value = editPhone, onValueChange = { editPhone = it })
                        EditField(label = "Daily Rate (GHS)", value = editDailyRate, onValueChange = { editDailyRate = it }, isNumber = true)
                        EditField(label = "Total Loan (GHS)", value = editTotalLoan, onValueChange = { editTotalLoan = it }, isNumber = true)
                        EditField(label = "Term (days)", value = editTermDays, onValueChange = { editTermDays = it }, isNumber = true)

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("KYC Verification Photos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (acc.customerPhotoPath != null && editCustomerPhoto == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (customerPhotoBitmap != null) {
                                    Image(
                                        bitmap = customerPhotoBitmap!!.asImageBitmap(),
                                        contentDescription = "Selfie",
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewerBitmap = customerPhotoBitmap },
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("✓ Selfie Photo is already uploaded. Upload to replace.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        KycPhotoSelector(
                            label = "Customer Photo (Selfie)",
                            base64 = editCustomerPhoto,
                            onPhotoSelected = { editCustomerPhoto = it }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (acc.nationalIdFrontPath != null && editNationalIdFront == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (idFrontBitmap != null) {
                                    Image(
                                        bitmap = idFrontBitmap!!.asImageBitmap(),
                                        contentDescription = "ID Front",
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewerBitmap = idFrontBitmap },
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("✓ National ID Front is already uploaded. Upload to replace.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        KycPhotoSelector(
                            label = "National ID Front Photo",
                            base64 = editNationalIdFront,
                            onPhotoSelected = { editNationalIdFront = it }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (acc.nationalIdBackPath != null && editNationalIdBack == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (idBackBitmap != null) {
                                    Image(
                                        bitmap = idBackBitmap!!.asImageBitmap(),
                                        contentDescription = "ID Back",
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewerBitmap = idBackBitmap },
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("✓ National ID Back is already uploaded. Upload to replace.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        KycPhotoSelector(
                            label = "National ID Back Photo",
                            base64 = editNationalIdBack,
                            onPhotoSelected = { editNationalIdBack = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { saveEditing() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                enabled = !isSaving && editName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(360.dp)
                            ) {
                                if (isSaving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                else ButtonText("Save")
                            }
                            OutlinedButton(
                                onClick = { cancelEditing() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(360.dp)
                            ) {
                                ButtonText("Cancel")
                            }
                        }
                    }
                }
            }

            InfoCard(title = "Customer Information") {
                InfoRow(icon = Icons.Filled.Person, label = "Name", value = acc.customerName)
                InfoRow(icon = Icons.Filled.Phone, label = "Phone", value = acc.phoneNumber)
                InfoRow(icon = Icons.Filled.Lock, label = "National ID", value = acc.nationalId)
            }

            InfoCard(title = "Device & Plan") {
                InfoRow(icon = null, label = "IMEI", value = acc.imei)
                InfoRow(icon = null, label = "Model", value = acc.deviceModel)
                InfoRow(icon = null, label = "Plan", value = acc.planName)
                InfoRow(icon = null, label = "Term", value = "${acc.termDays} days")
                InfoRow(icon = null, label = "Daily Rate", value = formatAmount(acc.dailyRate))
            }

            InfoCard(title = "Financial Summary") {
                InfoRow(icon = null, label = "Total Loan", value = formatAmount(acc.totalLoanAmount))
                InfoRow(icon = null, label = "Amount Paid", value = formatAmount(acc.amountPaid))
                InfoRow(icon = null, label = "Remaining", value = formatAmount(acc.remainingBalance))
                InfoRow(icon = null, label = "Down Payment", value = formatAmount(acc.downPayment))
            }

            if (customerPhotoBitmap != null || idFrontBitmap != null || idBackBitmap != null) {
                InfoCard(title = "KYC Verification Photos") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (customerPhotoBitmap != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    bitmap = customerPhotoBitmap!!.asImageBitmap(),
                                    contentDescription = "Selfie",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewerBitmap = customerPhotoBitmap },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Customer Selfie", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    Text("Captured live during registration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (idFrontBitmap != null || idBackBitmap != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (idFrontBitmap != null) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ID Front", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(
                                            bitmap = idFrontBitmap!!.asImageBitmap(),
                                            contentDescription = "ID Front",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewerBitmap = idFrontBitmap },
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                                if (idBackBitmap != null) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ID Back", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(
                                            bitmap = idBackBitmap!!.asImageBitmap(),
                                            contentDescription = "ID Back",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewerBitmap = idBackBitmap },
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            acc.let { a ->
                if (a.ghanaCardVerified != true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (verifyMessage != null) {
                        Text(
                            text = verifyMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            verifyInProgress = true
                            verifyMessage = null
                            scope.launch {
                                val result = repository?.verifyIdentity(a.id)
                                result?.fold(
                                    onSuccess = { response ->
                                        verifyInProgress = false
                                        if (response.sessionToken != null) {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(response.url ?: "https://verify.didit.me"))
                                            view.context.startActivity(intent)
                                            verifyMessage = "Verification flow opened in browser."
                                        } else {
                                            verifyMessage = "Verification session created. Check the dashboard."
                                        }
                                    },
                                    onFailure = {
                                        verifyInProgress = false
                                        verifyMessage = it.message ?: "Verification failed"
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !verifyInProgress && !actionInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(360.dp)
                    ) {
                        if (verifyInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ButtonText("Verify Identity")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { showMoMoDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !actionInProgress && !isEditing && acc.remainingBalance > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color(0xFF07130F)
                ),
                shape = RoundedCornerShape(360.dp)
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                ButtonText("Pay with Mobile Money")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showPaymentSheet = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !actionInProgress && !isEditing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(360.dp)
                ) {
                    Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    ButtonText("Cash / Record")
                }

                if (acc.status == AccountStatus.LOCKED) {
                    OutlinedButton(
                        onClick = {
                            actionInProgress = true
                            actionMessage = null
                            scope.launch {
                                val result = repository?.forceUnlock(acc.id)
                                result?.fold(
                                    onSuccess = { updated ->
                                        account = updated
                                        actionMessage = "Unlock command sent. Ask the customer phone to tap Sync Now if it is still locked."
                                    },
                                    onFailure = { actionMessage = it.message ?: "Unlock failed" }
                                ) ?: run { actionMessage = "Repository unavailable" }
                                actionInProgress = false
                                loadAccount()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !actionInProgress && !isEditing,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(360.dp)
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        ButtonText("Unlock")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            actionInProgress = true
                            actionMessage = null
                            scope.launch {
                                val result = repository?.forceLock(acc.id)
                                result?.fold(
                                    onSuccess = { updated ->
                                        account = updated
                                        actionMessage = "Lock command sent. The phone will lock on push, heartbeat, or next Sync Now."
                                    },
                                    onFailure = { actionMessage = it.message ?: "Lock failed" }
                                ) ?: run { actionMessage = "Repository unavailable" }
                                actionInProgress = false
                                loadAccount()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !actionInProgress && !isEditing,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(360.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        ButtonText("Force Lock")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onProvisionDevice(acc.imei) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isEditing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(360.dp)
            ) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                ButtonText("Regenerate QR")
            }

            OutlinedButton(
                onClick = {
                    actionInProgress = true
                    actionMessage = null
                    scope.launch {
                        val result = repository?.resetCustomerPin(acc.id)
                        result?.fold(
                            onSuccess = { customerCredentials = it },
                            onFailure = { actionMessage = it.message ?: "Unable to reset customer PIN" }
                        ) ?: run { actionMessage = "Repository unavailable" }
                        actionInProgress = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !actionInProgress && !isEditing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(360.dp)
            ) {
                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                ButtonText("Reset customer login PIN")
            }

            OutlinedButton(
                onClick = {
                    actionInProgress = true
                    scope.launch {
                        repository?.approveRelease(acc.id, acc.remainingBalance > 0)
                        actionInProgress = false
                        loadAccount()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !actionInProgress && !acc.releaseApproved && !isEditing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(360.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                ButtonText(if (acc.remainingBalance > 0) "Approve Release" else "Approve Removal")
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Danger Zone", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        actionInProgress = true
                        actionMessage = null
                        val targetStolen = !acc.isStolen
                        scope.launch {
                            val result = repository?.updateAccount(acc.id, UpdateAccountRequest(isStolen = targetStolen))
                            result?.fold(
                                onSuccess = { updated ->
                                    account = updated
                                    actionMessage = if (targetStolen) {
                                        "Stolen mode enabled. The phone will lock and start reporting GPS after its next sync."
                                    } else {
                                        "Stolen mode cleared and unlock command sent."
                                    }
                                },
                                onFailure = { actionMessage = it.message ?: "Stolen-state update failed" }
                            ) ?: run { actionMessage = "Repository unavailable" }
                            actionInProgress = false
                            loadAccount()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !actionInProgress && !isEditing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (acc.isStolen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(360.dp)
                ) {
                    ButtonText(if (acc.isStolen) "Recover + Unlock" else "Flag as Stolen")
                }

                OutlinedButton(
                    onClick = {
                        actionInProgress = true
                        scope.launch {
                            val result = repository?.deleteAccount(acc.id)
                            actionInProgress = false
                            result?.fold(
                                onSuccess = { onBack() },
                                onFailure = { error = it.message }
                            ) ?: onBack()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !actionInProgress && !isEditing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(360.dp)
                ) {
                    ButtonText("Delete")
                }
            }

            if (acc.isStolen || acc.status == AccountStatus.STOLEN) {
                Button(
                    onClick = { onViewLiveLocation(acc.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp),
                    enabled = !actionInProgress && !isEditing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(360.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    ButtonText("View Live Location")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPaymentSheet) {
        PaymentBottomSheet(
            accountId = accountId,
            repository = repository,
            onDismiss = { showPaymentSheet = false },
            onSuccess = {
                showPaymentSheet = false
                loadAccount()
            }
        )
    }

    if (showMoMoDialog && repository != null && account != null) {
        AgentPayWithMoMoDialog(
            repository = repository,
            account = account,
            onDismiss = { showMoMoDialog = false },
            onPaymentRecorded = {
                showMoMoDialog = false
                actionMessage = "Payment successful — device will unlock on next sync."
                loadAccount()
            }
        )
    }

    if (viewerBitmap != null) {
        ImageViewerDialog(
            bitmap = viewerBitmap!!,
            onDismiss = { viewerBitmap = null }
        )
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isNumber: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(360.dp)
        )
    }
}

@Composable
private fun StatusBanner(status: AccountStatus, releaseApproved: Boolean = false) {
    val (text, color) = if (releaseApproved) {
        "Release approved — customer app can be removed" to Color(0xFF10B981)
    } else when (status) {
        AccountStatus.ACTIVE -> "Active" to Color(0xFF10B981)
        AccountStatus.WARNING -> "Warning — Payment Due Soon" to Color(0xFFF59E0B)
        AccountStatus.LOCKED -> "Locked — Payment Overdue" to Color(0xFFDC2626)
        AccountStatus.STOLEN -> "Stolen — Device Flagged Stolen" to Color(0xFFDC2626)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentBottomSheet(
    accountId: String,
    repository: SecurePayRepository?,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("MOBILE_MONEY") }
    var reference by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        focusedContainerColor = MaterialTheme.colorScheme.background,
        unfocusedContainerColor = MaterialTheme.colorScheme.background,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Record Payment", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Amount (GH₵)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input ->
                        val filtered = input.filter { c -> c.isDigit() || c == '.' }
                        if (filtered.count { it == '.' } <= 1 && filtered.substringAfter('.', "").length <= 2) amount = filtered
                    },
                    placeholder = { Text("Enter amount", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = inputColors,
                    shape = RoundedCornerShape(360.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Payment Method", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MOBILE_MONEY", "CASH", "BANK").forEach { m ->
                        val isSelected = method == m
                        Button(
                            onClick = { method = m },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(360.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            ButtonText(if (m == "MOBILE_MONEY") "MOBILE MONEY" else m, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reference (optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    placeholder = { Text("Enter reference", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = inputColors,
                    shape = RoundedCornerShape(360.dp)
                )
            }

            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val amountCents = amount.toBigDecimalOrNull()?.movePointRight(2)?.toInt()
                    if (amountCents == null || amountCents <= 0) {
                        errorMessage = "Enter a valid amount"
                        return@Button
                    }
                    isSubmitting = true
                    scope.launch {
                        val result = repository?.recordPayment(
                            com.touchbase.agent.data.model.RecordPaymentRequest(
                                accountId = accountId,
                                amount = amountCents,
                                method = method,
                                reference = reference.ifBlank { null }
                            )
                        )
                        isSubmitting = false
                        result?.fold(
                            onSuccess = { onSuccess() },
                            onFailure = { errorMessage = it.message ?: "Payment failed" }
                        ) ?: onSuccess()
                    }
                },
                enabled = !isSubmitting && amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(360.dp)
            ) {
                if (isSubmitting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                else ButtonText("Record")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CustomerDetailScreenPreview() {
    SecurePayAgentTheme {
        CustomerDetailScreen(
            accountId = "1",
            repository = null,
            onBack = {}
        )
    }
}
