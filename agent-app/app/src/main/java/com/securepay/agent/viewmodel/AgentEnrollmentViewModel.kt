package com.securepay.agent.viewmodel

import androidx.lifecycle.ViewModel
import com.securepay.agent.domain.AgentUiState
import com.securepay.agent.domain.EnrollmentDraft
import com.securepay.agent.domain.HardwareData
import com.securepay.agent.domain.KycData
import com.securepay.agent.domain.PlanSelection
import com.securepay.agent.domain.RepaymentPlan
import com.securepay.agent.domain.WizardStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class AgentEnrollmentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState

    fun updateKyc(update: KycData.() -> KycData) {
        updateDraft { copy(kyc = kyc.update()) }
    }

    fun updateHardware(update: HardwareData.() -> HardwareData) {
        updateDraft { copy(hardware = hardware.update()) }
    }

    fun updatePlan(plan: RepaymentPlan) {
        updateDraft { copy(plan = this.plan.copy(selectedPlan = plan)) }
    }

    fun updateDownpayment(input: String) {
        val cents = input.filter(Char::isDigit).toLongOrNull()?.times(100L) ?: 0L
        updateDraft { copy(plan = plan.copy(downpaymentCents = cents)) }
    }

    fun nextStep() {
        _uiState.update { state ->
            state.copy(
                currentStep = when (state.currentStep) {
                    WizardStep.KYC -> WizardStep.HARDWARE
                    WizardStep.HARDWARE -> WizardStep.PLAN
                    WizardStep.PLAN -> WizardStep.PLAN
                }
            )
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            state.copy(
                currentStep = when (state.currentStep) {
                    WizardStep.KYC -> WizardStep.KYC
                    WizardStep.HARDWARE -> WizardStep.KYC
                    WizardStep.PLAN -> WizardStep.HARDWARE
                }
            )
        }
    }

    fun submitDraft() {
        _uiState.update { state ->
            if (state.draft.readyForTransmission) {
                state.copy(submittedPayload = state.draft.toNetworkPayload())
            } else {
                state
            }
        }
    }

    private fun updateDraft(update: EnrollmentDraft.() -> EnrollmentDraft) {
        _uiState.update { state ->
            state.copy(draft = state.draft.update(), submittedPayload = null)
        }
    }
}
