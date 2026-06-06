package com.securepay.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.securepay.agent.domain.PaymentPlan
import com.securepay.agent.ui.theme.CharcoalSurface
import com.securepay.agent.ui.theme.CharcoalSurfaceHigh
import com.securepay.agent.ui.theme.DeepCharcoal
import com.securepay.agent.ui.theme.EmeraldGreen
import com.securepay.agent.ui.theme.MutedText
import com.securepay.agent.viewmodel.EnrollmentFormState
import com.securepay.agent.viewmodel.EnrollmentStep
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEnrollmentScreen(
    state: EnrollmentFormState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onNationalIdChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onScanComplete: (String, String) -> Unit,
    onDownPaymentChange: (String) -> Unit,
    onPlanSelected: (PaymentPlan) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit
) {
    Scaffold(
        containerColor = DeepCharcoal,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SecurePay Agent",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepCharcoal,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepCharcoal)
                .padding(padding)
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            StepIndicator(state)

            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (state.activeStep) {
                        EnrollmentStep.KYC -> KycStep(
                            state = state,
                            onFirstNameChange = onFirstNameChange,
                            onLastNameChange = onLastNameChange,
                            onNationalIdChange = onNationalIdChange,
                            onPhoneChange = onPhoneChange,
                            onAddressChange = onAddressChange
                        )
                        EnrollmentStep.HARDWARE -> HardwareScanStep(
                            state = state,
                            onScanComplete = onScanComplete
                        )
                        EnrollmentStep.PAYMENT -> PaymentStep(
                            state = state,
                            onDownPaymentChange = onDownPaymentChange,
                            onPlanSelected = onPlanSelected
                        )
                    }
                }
            }

            if (state.submissionMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = EmeraldGreen.copy(alpha = 0.14f),
                    contentColor = EmeraldGreen,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = state.submissionMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            WizardActions(
                state = state,
                onBack = onBack,
                onNext = onNext,
                onSubmit = onSubmit
            )
        }
    }
}

@Composable
private fun StepIndicator(state: EnrollmentFormState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            state.steps.forEachIndexed { index, step ->
                val active = index <= state.currentStep
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (active) EmeraldGreen else CharcoalSurfaceHigh,
                        contentColor = if (active) DeepCharcoal else MutedText,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onBackground else MutedText
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { (state.currentStep + 1).toFloat() / state.steps.size.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = EmeraldGreen,
            trackColor = CharcoalSurfaceHigh
        )
    }
}

@Composable
private fun KycStep(
    state: EnrollmentFormState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onNationalIdChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAddressChange: (String) -> Unit
) {
    SectionTitle(icon = Icons.Default.Badge, title = "Customer KYC Data Entry")
    OutlinedTextField(
        value = state.kycData.firstName,
        onValueChange = onFirstNameChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("First name") }
    )
    OutlinedTextField(
        value = state.kycData.lastName,
        onValueChange = onLastNameChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Last name") }
    )
    OutlinedTextField(
        value = state.kycData.nationalId,
        onValueChange = onNationalIdChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("National ID") }
    )
    OutlinedTextField(
        value = state.kycData.phoneNumber,
        onValueChange = onPhoneChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Phone number") }
    )
    OutlinedTextField(
        value = state.kycData.residentialAddress,
        onValueChange = onAddressChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        label = { Text("Residential address") }
    )
}

@Composable
private fun HardwareScanStep(
    state: EnrollmentFormState,
    onScanComplete: (String, String) -> Unit
) {
    SectionTitle(icon = Icons.Default.CameraAlt, title = "Hardware Barcode / IMEI Scanner")
    CameraXPreview(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    )
    Text(
        text = "IMEI ${state.hardwareScan.imei.ifBlank { "not captured" }}",
        color = MutedText
    )
    Button(
        onClick = {
            onScanComplete("357249105864219", "SP-HW-357249105864219")
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EmeraldGreen,
            contentColor = DeepCharcoal
        )
    ) {
        Icon(imageVector = Icons.Default.Check, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Use Test IMEI Capture")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentStep(
    state: EnrollmentFormState,
    onDownPaymentChange: (String) -> Unit,
    onPlanSelected: (PaymentPlan) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    SectionTitle(icon = Icons.Default.Payments, title = "Downpayment & Plan Selection")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = state.selectedPlan.label,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            label = { Text("Payment plan") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PaymentPlan.entries.forEach { plan ->
                DropdownMenuItem(
                    text = {
                        Text("${plan.label} - ${formatCurrency(plan.monthlyPaymentCents)} / month")
                    },
                    onClick = {
                        onPlanSelected(plan)
                        expanded = false
                    }
                )
            }
        }
    }

    OutlinedTextField(
        value = state.downPaymentInput,
        onValueChange = onDownPaymentChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Downpayment in cents") }
    )

    Surface(
        color = CharcoalSurfaceHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Network payload preview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.toPayload()?.toString() ?: "Complete required fields to generate payload.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
    }
}

@Composable
private fun CameraXPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(
            modifier = modifier.background(CharcoalSurfaceHigh, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            TextButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Camera Permission")
            }
        }
        return
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val executor = ContextCompat.getMainExecutor(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }
                },
                executor
            )
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }
}

@Composable
private fun WizardActions(
    state: EnrollmentFormState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            enabled = state.canGoBack,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = null)
            Text("Back")
        }

        if (state.activeStep == EnrollmentStep.PAYMENT) {
            Button(
                onClick = onSubmit,
                enabled = state.canGoNext && !state.isSubmitting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen,
                    contentColor = DeepCharcoal
                )
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = DeepCharcoal
                    )
                } else {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text("Submit")
            }
        } else {
            Button(
                onClick = onNext,
                enabled = state.canGoNext,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen,
                    contentColor = DeepCharcoal
                )
            ) {
                Text("Next")
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = EmeraldGreen)
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatCurrency(cents: Long): String {
    return NumberFormat.getCurrencyInstance(Locale.US).format(cents / 100.0)
}

