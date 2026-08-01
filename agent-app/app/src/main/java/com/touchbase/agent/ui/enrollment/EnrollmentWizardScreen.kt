package com.touchbase.agent.ui.enrollment

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.ui.components.ButtonText
import com.touchbase.agent.ui.enrollment.steps.WizardSectionHeader
import com.touchbase.agent.ui.theme.SecurePayAgentTheme
import com.touchbase.agent.ui.theme.isLight

import com.touchbase.agent.ui.enrollment.steps.ConsentStep
import com.touchbase.agent.ui.enrollment.steps.ContactsStep
import com.touchbase.agent.ui.enrollment.steps.CustomerStep
import com.touchbase.agent.ui.enrollment.steps.DetailsStep
import com.touchbase.agent.ui.enrollment.steps.IdentityStep
import com.touchbase.agent.ui.enrollment.steps.IntroStep
import com.touchbase.agent.ui.enrollment.steps.LocationStep
import com.touchbase.agent.ui.enrollment.steps.ProductStep
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.touchbase.agent.data.local.EnrollmentDraftStore
import kotlinx.coroutines.launch

/**
 * M-KOPA "Start Application" flow, Touch Base edition.
 * Dark background, gold accents, dots progress, one small task per screen.
 */
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

    LaunchedEffect(state.submission) {
        val submission = state.submission
        if (submission is SubmissionState.Error) {
            snackbarHostState.showSnackbar(submission.message)
            viewModel.clearSubmissionError()
        }
    }

    val context = LocalContext.current
    val draftStore = remember(context) { EnrollmentDraftStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showResumeConfirm by remember { mutableStateOf(false) }
    var pendingSnapshot by remember { mutableStateOf<EnrollmentDraftSnapshot?>(null) }

    // Offer to resume a "Save & continue later" draft the moment the wizard opens.
    LaunchedEffect(Unit) {
        val saved = draftStore.load()
        if (saved != null) {
            pendingSnapshot = saved
            showResumeConfirm = true
        }
    }

    // A successful submission invalidates any saved draft.
    LaunchedEffect(state.submission) {
        if (state.submission is SubmissionState.Success) draftStore.clear()
    }

    fun saveAndContinueLater() {
        scope.launch {
            draftStore.save(viewModel.snapshot())
            Toast.makeText(context, "Saved. You can resume this application later.", Toast.LENGTH_SHORT).show()
            onCancel()
        }
    }

    fun discardAndExit() {
        scope.launch {
            draftStore.clear()
            onCancel()
        }
    }

    // The Android system back gesture/button is intentionally disabled so a
    // half-finished application can never be lost to an accidental swipe. The
    // on-screen arrows (and the top-bar menu) are the only way to navigate.
    BackHandler(enabled = state.submission !is SubmissionState.Success) { /* swallow */ }

    val isPreview = LocalInspectionMode.current
    val view = LocalView.current
    val backgroundColor = MaterialTheme.colorScheme.background

    if (!isPreview) {
        SideEffect {
            val activity = view.context as Activity
            val window = activity.window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = backgroundColor.isLight()
            // The customer-consent / signature screen is laid out for landscape
            // (the signature pad is far easier to use wide, mirroring M-KOPA).
            // Force landscape only on that step and restore portrait everywhere
            // else — including the success screen and every other wizard step.
            val consentLandscape = state.submission !is SubmissionState.Success &&
                state.currentStep == EnrollmentStep.CONSENT
            activity.requestedOrientation = if (consentLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // Resume a previously saved ("continue later") application, or start fresh.
    if (showResumeConfirm) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Resume application?") },
            text = { Text("You have an unfinished application saved on this device. Continue where you left off, or start a new one?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSnapshot?.let { viewModel.restore(it) }
                        pendingSnapshot = null
                        showResumeConfirm = false
                    }
                ) { Text("Resume") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { draftStore.clear() }
                        pendingSnapshot = null
                        showResumeConfirm = false
                    }
                ) { Text("Start new") }
            }
        )
    }

    // First-screen arrow: choose between discarding, saving for later, or staying.
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Leave application?") },
            text = { Text("Your progress is not submitted yet. Save it to finish later, or discard it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        saveAndContinueLater()
                    }
                ) { Text("Save & continue later") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showExitConfirm = false
                            showDiscardConfirm = true
                        }
                    ) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") }
                }
            }
        )
    }

    // Final guard before throwing an in-progress application away.
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard application?") },
            text = { Text("This permanently deletes the in-progress application. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        discardAndExit()
                    }
                ) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Start Application",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                state.submission is SubmissionState.Success -> onCancel()
                                state.isFirstStep -> showExitConfirm = true
                                else -> viewModel.prevStep()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (state.submission !is SubmissionState.Success) {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save & continue later") },
                                onClick = {
                                    showOverflowMenu = false
                                    saveAndContinueLater()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Discard", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOverflowMenu = false
                                    showDiscardConfirm = true
                                }
                            )
                        }
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
                // M-KOPA dots: 6 sections, joined by progress lines.
                WizardProgressDots(
                    totalSteps = EnrollmentStep.DOT_COUNT,
                    currentIndex = state.currentStep.dot,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (state.currentStep == EnrollmentStep.CONSENT) {
                    // Long consent title (3 lines) — plain bold text like M-KOPA.
                    Text(
                        state.currentSection,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                } else if (state.currentStep != EnrollmentStep.INTRO) {
                    WizardSectionHeader(
                        title = state.currentSection,
                        icon = com.touchbase.agent.ui.enrollment.steps.sectionIcon(state.currentStep)
                    )
                }
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
                            EnrollmentStep.INTRO -> IntroStep()
                            EnrollmentStep.CUSTOMER -> CustomerStep(
                                state = state,
                                onFirstNameChange = viewModel::updateFirstName,
                                onSurnameChange = viewModel::updateSurname,
                                onIdTypeChange = viewModel::updateIdType,
                                onNationalIdChange = viewModel::updateNationalId,
                                onPhoneChange = viewModel::updatePhone,
                                onOtherPhoneChange = viewModel::updateOtherPhone
                            )
                            EnrollmentStep.DETAILS -> DetailsStep(
                                state = state,
                                onDateOfBirthChange = viewModel::updateDateOfBirth,
                                onMaritalChange = viewModel::updateMaritalStatus,
                                onEmploymentChange = viewModel::updateEmploymentStatus,
                                onGenderChange = viewModel::updateGender,
                                onIsCustomerUserChange = viewModel::updateIsCustomerUser
                            )
                            EnrollmentStep.CONTACTS -> ContactsStep(
                                state = state,
                                onKinNameChange = viewModel::updateNextOfKinName,
                                onKinRelationChange = viewModel::updateNextOfKinRelation,
                                onKinPhoneChange = viewModel::updateNextOfKinPhone,
                                onRefereeNameChange = viewModel::updateRefereeName,
                                onRefereePhoneChange = viewModel::updateRefereePhone,
                                onGuarantorNameChange = viewModel::updateGuarantorName,
                                onGuarantorRelationChange = viewModel::updateGuarantorRelation,
                                onGuarantorPhoneChange = viewModel::updateGuarantorPhone,
                                onGuarantorIdChange = viewModel::updateGuarantorIdNumber
                            )
                            EnrollmentStep.IDENTITY -> IdentityStep(
                                state = state,
                                onIdFrontSelected = viewModel::updateIdFront,
                                onIdBackSelected = viewModel::updateIdBack,
                                onPhotoSelected = viewModel::updateCustomerPhoto
                            )
                            EnrollmentStep.LOCATION -> LocationStep(
                                state = state,
                                onRegionChange = viewModel::updateRegion,
                                onDistrictChange = viewModel::updateDistrict,
                                onAddressChange = viewModel::updatePhysicalAddress,
                                onLanguageChange = viewModel::updatePreferredLanguage
                            )
                            EnrollmentStep.PRODUCT, EnrollmentStep.OFFERS, EnrollmentStep.LOAN -> ProductStep(
                                state = state,
                                phase = state.currentStep,
                                onSelectDevice = viewModel::selectDevice,
                                onRefreshDevices = viewModel::refreshDevices,
                                onDailyRateChange = viewModel::updateDailyRate,
                                onTotalAmountChange = viewModel::updateTotalAmount,
                                onTermDaysChange = viewModel::updateTermDays,
                                onDownPaymentChange = viewModel::updateDownPayment,
                                onImeiChange = viewModel::updateImei,
                                onDeviceModelChange = viewModel::updateDeviceModel,
                                onEditSerial = { viewModel.goToStep(EnrollmentStep.PRODUCT.ordinal) }
                            )
                            EnrollmentStep.VERIFY -> com.touchbase.agent.ui.enrollment.steps.VerificationPassedStep()
                            EnrollmentStep.CONSENT -> ConsentStep(
                                state = state,
                                agreementText = viewModel.buildAgreement(),
                                onConsentChecked = viewModel::updateConsentTerms,
                                onSignatureChange = viewModel::updateSignature
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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

/** M-KOPA stepper: filled dots joined by a progress line. */
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
                        .width(24.dp)
                        .height(2.dp)
                        .background(lineColor)
                )
            }
        }
    }
}

/** M-KOPA bottom bar: circular back button left, big rectangular action right. */
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.isFirstStep) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = !state.isSubmitting
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        val enabled = if (state.isLastStep) state.isSubmitReady else state.isCurrentStepValid
        Button(
            onClick = if (state.isLastStep) onSubmit else onNext,
            enabled = enabled && !state.isSubmitting,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            ButtonText(
                when {
                    state.isSubmitting -> "SUBMITTING…"
                    state.isLastStep -> "AGREE & SUBMIT"
                    state.isFirstStep -> "CONTINUE"
                    else -> "NEXT"
                }
            )
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SuccessRow(label = "Reference", value = enrollmentId)
                if (accountNumber.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SuccessRow(label = "Customer account", value = accountNumber, mono = true)
                }
                if (temporaryPin.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            ButtonText("NEXT — Provision this device")
        }
        OutlinedButton(
            onClick = onDone,
            shape = RoundedCornerShape(8.dp),
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
