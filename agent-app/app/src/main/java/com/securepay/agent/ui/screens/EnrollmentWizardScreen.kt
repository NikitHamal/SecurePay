package com.securepay.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securepay.agent.data.model.WizardStep
import com.securepay.agent.ui.components.StepIndicator
import com.securepay.agent.ui.screens.steps.KycStep
import com.securepay.agent.ui.screens.steps.PaymentStep
import com.securepay.agent.ui.screens.steps.ScannerStep
import com.securepay.agent.ui.viewmodel.EnrollmentViewModel

/**
 * Hosts the three-step enrollment wizard: KYC -> Scanner -> Payment. Shows a
 * success screen once the enrollment has been submitted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollmentWizardScreen(
    modifier: Modifier = Modifier,
    viewModel: EnrollmentViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("New Enrollment") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        if (state.isSubmitted) {
            EnrollmentSuccess(
                enrollmentId = state.enrollmentId.orEmpty(),
                message = state.submitMessage.orEmpty(),
                onStartNew = viewModel::reset,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            StepIndicator(currentStep = state.currentStep)

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (state.currentStep) {
                    WizardStep.KYC -> KycStep(
                        data = state.draft,
                        onUpdate = { name, id, phone ->
                            viewModel.updateKyc(name, id, phone)
                        }
                    )

                    WizardStep.SCANNER -> ScannerStep(
                        data = state.draft,
                        onImeiScanned = viewModel::onImeiScanned,
                        onDeviceModelChange = viewModel::updateDeviceModel
                    )

                    WizardStep.PAYMENT -> PaymentStep(
                        data = state.draft,
                        selectedPlan = state.selectedPlan,
                        totalAmountInput = state.totalAmountInput,
                        downPaymentInput = state.downPaymentInput,
                        isSubmitting = state.isSubmitting,
                        onAmountsChange = viewModel::updateAmounts,
                        onPlanSelected = viewModel::selectPlan,
                        onSubmit = viewModel::submit
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            WizardNavBar(
                showBack = !state.isFirstStep,
                showNext = !state.isLastStep,
                canAdvance = state.canAdvance,
                onBack = viewModel::back,
                onNext = viewModel::next
            )
        }
    }
}

@Composable
private fun WizardNavBar(
    showBack: Boolean,
    showNext: Boolean,
    canAdvance: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                OutlinedButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(text = "  Back")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            if (showNext) {
                Button(
                    onClick = onNext,
                    enabled = canAdvance
                ) {
                    Text(text = "Next  ")
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
        }
    }
}

@Composable
private fun EnrollmentSuccess(
    enrollmentId: String,
    message: String,
    onStartNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.size(24.dp))
        Text(
            text = "Enrollment Complete",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "Enrollment ID",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = enrollmentId,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(32.dp))
        Button(
            onClick = onStartNew,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start New Enrollment")
        }
    }
}
