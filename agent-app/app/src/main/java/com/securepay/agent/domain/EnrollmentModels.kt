package com.securepay.agent.domain

enum class WizardStep(val label: String) {
    KYC("KYC"),
    HARDWARE("Device"),
    PLAN("Plan")
}

enum class RepaymentPlan(val label: String, val dailyRateCents: Long, val durationDays: Int) {
    DAILY_180("Daily - 180 days", 250_00, 180),
    WEEKLY_52("Weekly - 52 weeks", 1_650_00, 364),
    MONTHLY_12("Monthly - 12 months", 6_500_00, 365)
}

data class KycData(
    val fullName: String = "",
    val nationalId: String = "",
    val phoneNumber: String = "",
    val region: String = ""
)

data class HardwareData(
    val imei: String = "",
    val barcode: String = ""
)

data class PlanSelection(
    val selectedPlan: RepaymentPlan = RepaymentPlan.DAILY_180,
    val downpaymentCents: Long = 0L
)

data class EnrollmentDraft(
    val kyc: KycData = KycData(),
    val hardware: HardwareData = HardwareData(),
    val plan: PlanSelection = PlanSelection()
) {
    val readyForTransmission: Boolean
        get() = kyc.fullName.isNotBlank() &&
            kyc.nationalId.isNotBlank() &&
            kyc.phoneNumber.isNotBlank() &&
            hardware.imei.length >= 14 &&
            plan.downpaymentCents > 0L

    fun toNetworkPayload(): Map<String, Any> = mapOf(
        "customer" to mapOf(
            "fullName" to kyc.fullName.trim(),
            "nationalId" to kyc.nationalId.trim(),
            "phoneNumber" to kyc.phoneNumber.trim(),
            "region" to kyc.region.trim()
        ),
        "hardware" to mapOf(
            "imei" to hardware.imei.trim(),
            "barcode" to hardware.barcode.trim()
        ),
        "financing" to mapOf(
            "plan" to plan.selectedPlan.name,
            "dailyRateCents" to plan.selectedPlan.dailyRateCents,
            "durationDays" to plan.selectedPlan.durationDays,
            "downpaymentCents" to plan.downpaymentCents
        )
    )
}

data class AgentUiState(
    val currentStep: WizardStep = WizardStep.KYC,
    val draft: EnrollmentDraft = EnrollmentDraft(),
    val submittedPayload: Map<String, Any>? = null
)
