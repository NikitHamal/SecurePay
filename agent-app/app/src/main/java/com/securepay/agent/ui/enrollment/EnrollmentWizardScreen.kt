package com.securepay.agent.ui.enrollment

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securepay.agent.R
import com.securepay.agent.data.remote.SecurePayRepository
import com.securepay.agent.ui.components.StepIndicator
import com.securepay.agent.ui.enrollment.steps.KycStep
import com.securepay.agent.ui.enrollment.steps.PlanStep
import com.securepay.agent.ui.enrollment.steps.ScannerStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollmentWizardScreen(
    repository: SecurePayRepository,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wizard_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                    onDone = onComplete,
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
                                onPhoneChange = viewModel::updateKycPhone
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
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.action_back))
        }

        if (state.isLastStep) {
            Button(
                onClick = onSubmit,
                enabled = state.isPlanStepValid && !state.isSubmitting,
                modifier = Modifier.weight(1f)
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
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_next))
            }
        }
    }
}

@Composable
private fun EnrollmentSuccess(
    enrollmentId: String,
    onDone: () -> Unit,
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
        Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) {
            Text("Done")
        }
    }
}