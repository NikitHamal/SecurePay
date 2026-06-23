package com.touchbase.agent.ui.enrollment

import android.app.Activity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touchbase.agent.R
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.components.StepIndicator
import com.touchbase.agent.ui.enrollment.steps.KycStep
import com.touchbase.agent.ui.enrollment.steps.PlanStep
import com.touchbase.agent.ui.enrollment.steps.ScannerStep

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
        stringResource(R.string.step_device),
        stringResource(R.string.step_plan)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StepIndicator(
                currentIndex = state.stepIndex,
                labels = stepLabels,
                modifier = Modifier.padding(top = 8.dp)
            )

            val submission = state.submission
            if (submission is SubmissionState.Success) {
                EnrollmentSuccess(
                    enrollmentId = submission.enrollmentId,
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
                                onPhoneChange = viewModel::updateKycPhone,
                                onPhotoSelected = viewModel::updateKycPhoto,
                                onIdFrontSelected = viewModel::updateKycIdFront,
                                onIdBackSelected = viewModel::updateKycIdBack
                            )
                            EnrollmentStep.DEVICE -> ScannerStep(
                                state = state,
                                onImeiChange = viewModel::updateImei,
                                onDeviceModelChange = viewModel::updateDeviceModel
                            )
                            EnrollmentStep.PLAN -> PlanStep(
                                state = state,
                                onSelectPlan = viewModel::selectPlan,
                                onDownPaymentChange = viewModel::updateDownPayment
                            )
                        }
                    }
                }

                WizardControls(
                    state = state,
                    onBack = viewModel::prevStep,
                    onNext = viewModel::nextStep,
                    onSubmit = viewModel::submit,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun WizardControls(
    state: EnrollmentUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            enabled = !state.isFirstStep && !state.isSubmitting,
            modifier = Modifier.weight(1f).height(52.dp)
        ) {
            Text(stringResource(R.string.action_back), color = MaterialTheme.colorScheme.onBackground)
        }

        if (state.isLastStep) {
            Button(
                onClick = onSubmit,
                enabled = state.isPlanStepValid && !state.isSubmitting,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(stringResource(R.string.action_submit))
            }
        } else {
            Button(
                onClick = onNext,
                enabled = state.isCurrentStepValid,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text(stringResource(R.string.action_next))
            }
        }
    }
}

@Composable
private fun EnrollmentSuccess(
    enrollmentId: String,
    imei: String,
    onDone: () -> Unit,
    onProvision: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "Enrollment submitted!",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Reference: $enrollmentId",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onProvision,
            enabled = imei.length == 15,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Provision this device")
        }
        OutlinedButton(onClick = onDone) {
            Text("Done")
        }
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
