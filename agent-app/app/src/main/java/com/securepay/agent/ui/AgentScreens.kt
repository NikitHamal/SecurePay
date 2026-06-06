package com.securepay.agent.ui

import android.Manifest
import androidx.camera.core.CameraSelector
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securepay.agent.domain.AgentUiState
import com.securepay.agent.domain.RepaymentPlan
import com.securepay.agent.domain.WizardStep
import com.securepay.agent.viewmodel.AgentEnrollmentViewModel

@Composable
fun AgentApp(viewModel: AgentEnrollmentViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SecurePay Agent",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            StepHeader(state.currentStep)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (state.currentStep) {
                        WizardStep.KYC -> KycStep(state, viewModel)
                        WizardStep.HARDWARE -> HardwareStep(state, viewModel)
                        WizardStep.PLAN -> PlanStep(state, viewModel)
                    }
                }
            }
            NavigationRow(state, viewModel)
        }
    }
}

@Composable
private fun StepHeader(step: WizardStep) {
    val progress = (step.ordinal + 1) / WizardStep.entries.size.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            WizardStep.entries.forEach {
                Text(
                    text = it.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (it == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    }
}

@Composable
private fun KycStep(
    state: AgentUiState,
    viewModel: AgentEnrollmentViewModel
) {
    val kyc = state.draft.kyc
    StepTitle(Icons.Default.Badge, "Customer KYC")
    OutlinedTextField(
        value = kyc.fullName,
        onValueChange = { value -> viewModel.updateKyc { copy(fullName = value) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Full legal name") },
        singleLine = true
    )
    OutlinedTextField(
        value = kyc.nationalId,
        onValueChange = { value -> viewModel.updateKyc { copy(nationalId = value) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("National ID") },
        singleLine = true
    )
    OutlinedTextField(
        value = kyc.phoneNumber,
        onValueChange = { value -> viewModel.updateKyc { copy(phoneNumber = value) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Phone number") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true
    )
    OutlinedTextField(
        value = kyc.region,
        onValueChange = { value -> viewModel.updateKyc { copy(region = value) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Region / route") },
        singleLine = true
    )
}

@Composable
private fun HardwareStep(
    state: AgentUiState,
    viewModel: AgentEnrollmentViewModel
) {
    val hardware = state.draft.hardware
    StepTitle(Icons.Default.CameraAlt, "Hardware Scan")
    CameraScannerPlaceholder()
    OutlinedTextField(
        value = hardware.imei,
        onValueChange = { value -> viewModel.updateHardware { copy(imei = value.filter(Char::isDigit)) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("IMEI") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
    OutlinedTextField(
        value = hardware.barcode,
        onValueChange = { value -> viewModel.updateHardware { copy(barcode = value) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Hardware barcode") },
        singleLine = true
    )
    Text(
        text = "CameraX preview is initialized here; barcode decoding can be attached to the image analysis pipeline.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlanStep(
    state: AgentUiState,
    viewModel: AgentEnrollmentViewModel
) {
    val plan = state.draft.plan
    var expanded by remember { mutableStateOf(false) }
    StepTitle(Icons.Default.CreditCard, "Downpayment and Plan")
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = plan.selectedPlan.label,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            label = { Text("Repayment plan") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            RepaymentPlan.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        viewModel.updatePlan(option)
                        expanded = false
                    }
                )
            }
        }
    }
    OutlinedTextField(
        value = if (plan.downpaymentCents == 0L) "" else (plan.downpaymentCents / 100L).toString(),
        onValueChange = viewModel::updateDownpayment,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Downpayment amount (KES)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
    Button(
        onClick = viewModel::submitDraft,
        enabled = state.draft.readyForTransmission,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.CloudUpload, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Prepare Network Payload")
    }
    state.submittedPayload?.let {
        Text(
            text = it.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StepTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CameraScannerPlaceholder() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            }
        }
        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Text(
            text = Manifest.permission.CAMERA,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
        )
    }
}

@Composable
private fun NavigationRow(
    state: AgentUiState,
    viewModel: AgentEnrollmentViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(
            onClick = viewModel::previousStep,
            enabled = state.currentStep != WizardStep.KYC
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null)
            Text("Back")
        }
        if (state.currentStep == WizardStep.PLAN) {
            TextButton(onClick = viewModel::submitDraft, enabled = state.draft.readyForTransmission) {
                Text("Validate")
            }
        } else {
            Button(onClick = viewModel::nextStep) {
                Text("Next")
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}
