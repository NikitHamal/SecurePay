package com.touchbase.agent.ui.enrollment

import android.app.Activity
import androidx.core.view.WindowInsetsControllerCompat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touchbase.agent.R
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.isLight
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.components.ButtonText

import com.touchbase.agent.ui.enrollment.steps.ConsentStep
import com.touchbase.agent.ui.enrollment.steps.ContactsStep
import com.touchbase.agent.ui.enrollment.steps.KycStep
import com.touchbase.agent.ui.enrollment.steps.PlanStep
import com.touchbase.agent.ui.enrollment.steps.ReviewStep
import com.touchbase.agent.ui.enrollment.steps.ScannerStep
import com.touchbase.agent.ui.enrollment.steps.SignerStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollmentWizardScreen(
    repository: SecurePayRepository?,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onProvisionDevice: (imei: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel = remember { EnrollmentViewModel(repository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val stepLabels = listOf(
        stringResource(R.string.step_kyc),
        stringResource(R.string.step_references),
        stringResource(R.string.step_signer),
        stringResource(R.string.step_device),
        stringResource(R.string.step_plan),
        stringResource(R.string.step_consent),
        stringResource(R.string.step_review)
    )

    LaunchedEffect(state.submission) {
        val submission = state.submission
        if (submission is SubmissionState.Error) {
            snackbarHostState.showSnackbar(submission.message)
            viewModel.clearSubmissionError()
        }
    }

    val isPreview = LocalInspectionMode.current
    val view = LocalView.current
    val backgroundColor = MaterialTheme.colorScheme.background

    if (!isPreview) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = backgroundColor.isLight()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wizard_title), color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    scrolledContainerColor = backgroundColor
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val submission = state.submission
            if (submission !is SubmissionState.Success) {
                WizardProgressDots(
                    totalSteps = stepLabels.size,
                    currentIndex = state.stepIndex,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = sectionTitle(state.currentStep),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (submission is SubmissionState.Success) {
                EnrollmentSuccess(
                    enrollmentId = submission.enrollmentId,
                    accountNumber = submission.accountNumber,
                    temporaryPin = submission.temporaryPin,
                    imei = state.draft.imei,
                    onDone = onComplete,
                    onProvision = { onProvisionDevice(state.draft.imei) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                AnimatedContent(
                    targetState = state.stepIndex,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val direction = if (forward) 1 else -1
                        (slideInHorizontally(tween(300)) { width -> direction * width } + fadeIn())
                            .togetherWith(
                                slideOutHorizontally(tween(300)) { width -> -direction * width } + fadeOut()
                            )
                            .using(SizeTransform(clip = false))
                    },
                    label = "stepContent",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { index ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (EnrollmentStep.ordered[index]) {
                            EnrollmentStep.KYC -> KycStep(
                                state = state,
                                onNameChange = viewModel::updateKycName,
                                onNationalIdChange = viewModel::updateKycNationalId,
                                onIdTypeChange = viewModel::updateKycIdType,
                                onPhoneChange = viewModel::updateKycPhone,
                                onPhotoSelected = viewModel::updateKycPhoto,
                                onIdFrontSelected = viewModel::updateKycIdFront,
                                onIdBackSelected = viewModel::updateKycIdBack
                            )
                            EnrollmentStep.REFERENCES -> ContactsStep(
                                state = state,
                                onKinNameChange = viewModel::updateNextOfKinName,
                                onKinRelationChange = viewModel::updateNextOfKinRelation,
                                onKinPhoneChange = viewModel::updateNextOfKinPhone,
                                onRefereeNameChange = viewModel::updateRefereeName,
                                onRefereePhoneChange = viewModel::updateRefereePhone
                            )
                            EnrollmentStep.SIGNER -> SignerStep(
                                state = state,
                                onNameChange = viewModel::updateGuarantorName,
                                onRelationChange = viewModel::updateGuarantorRelation,
                                onPhoneChange = viewModel::updateGuarantorPhone,
                                onIdNumberChange = viewModel::updateGuarantorIdNumber
                            )
                            EnrollmentStep.DEVICE -> ScannerStep(
                                state = state,
                                onImeiChange = viewModel::updateImei,
                                onDeviceModelChange = viewModel::updateDeviceModel,
                                onSelectDevice = viewModel::selectDevice,
                                onRefreshDevices = viewModel::refreshDevices
                            )
                            EnrollmentStep.PLAN -> PlanStep(
                                state = state,
                                onSelectPlan = viewModel::selectPlan,
                                onDailyRateChange = viewModel::updateDailyRate,
                                onTotalAmountChange = viewModel::updateTotalAmount,
                                onTermDaysChange = viewModel::updateTermDays,
                                onDownPaymentChange = viewModel::updateDownPayment
                            )
                            EnrollmentStep.CONSENT -> ConsentStep(
                                state = state,
                                onConsentTermsChange = viewModel::updateConsentTerms,
                                onConsentDataChange = viewModel::updateConsentData,
                                onSignatureChange = viewModel::updateSignature
                            )
                            EnrollmentStep.REVIEW -> ReviewStep(
                                state = state,
                                onEditStep = viewModel::goToStep
                            )
                        }
                    }
                }

                WizardControls(
                    state = state,
                    onBack = viewModel::prevStep,
                    onCancel = onCancel,
                    onNext = viewModel::nextStep,
                    onSubmit = viewModel::submit,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

private fun sectionTitle(step: EnrollmentStep): String = when (step) {
    EnrollmentStep.KYC -> "Identity verification"
    EnrollmentStep.REFERENCES -> "Next of kin & referee"
    EnrollmentStep.SIGNER -> "Guarantor (co-signer)"
    EnrollmentStep.DEVICE -> "Device selection"
    EnrollmentStep.PLAN -> "Financing plan"
    EnrollmentStep.CONSENT -> "Customer consent & signature"
    EnrollmentStep.REVIEW -> "Application review"
}

/** M-KOPA style stepper: filled dots joined by a progress line. */
@Composable
private fun WizardProgressDots(
    totalSteps: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        for (index in 0 until totalSteps) {
            val reached = index <= currentIndex
            val dotColor by animateColorAsState(
                targetValue = if (reached) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                label = "wizardDotColor$index"
            )
            val dotSize by animateDpAsState(
                targetValue = if (index == currentIndex) 12.dp else 10.dp,
                label = "wizardDotSize$index"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            if (index < totalSteps - 1) {
                val lineColor by animateColorAsState(
                    targetValue = if (index < currentIndex) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    label = "wizardLineColor$index"
                )
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(lineColor)
                )
            }
        }
    }
}

@Composable
private fun WizardControls(
    state: EnrollmentUiState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = if (state.isFirstStep) onCancel else onBack,
            enabled = !state.isSubmitting,
            modifier = Modifier.weight(1f).height(52.dp)
        ) {
            ButtonText(stringResource(R.string.action_back), color = MaterialTheme.colorScheme.onBackground)
        }

        if (state.isLastStep) {
            Button(
                onClick = onSubmit,
                enabled = state.isSubmitReady && !state.isSubmitting,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                ButtonText(stringResource(R.string.action_submit))
            }
        } else {
            Button(
                onClick = onNext,
                enabled = state.isCurrentStepValid,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                ButtonText(stringResource(R.string.action_next))
            }
        }
    }
}

@Composable
private fun EnrollmentSuccess(
    enrollmentId: String,
    accountNumber: String,
    temporaryPin: String,
    imei: String,
    onDone: () -> Unit,
    onProvision: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Big "Approved" banner — mirrors the verification-result card the client likes.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22C55E))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Approved",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "The application has been submitted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SuccessRow(label = "Reference", value = enrollmentId)
                if (accountNumber.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SuccessRow(label = "Customer account", value = accountNumber, mono = true)
                }
                if (temporaryPin.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SuccessRow(label = "Temporary PIN", value = temporaryPin, mono = true)
                }
            }
        }

        if (accountNumber.isNotBlank() && temporaryPin.isNotBlank()) {
            Text(
                "Give these details only to the verified customer. The PIN cannot be viewed again unless it is reset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onProvision,
            enabled = imei.length == 15,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            ButtonText(stringResource(R.string.action_next) + " — Provision this device")
        }
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            ButtonText("Done", color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SuccessRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun EnrollmentWizardScreenPreview() {
    SecurePayAgentTheme {
        EnrollmentWizardScreen(
            repository = null,
            onComplete = {},
            onCancel = {}
        )
    }
}
