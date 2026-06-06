package com.securepay.agent.domain

data class KycData(
    val firstName: String = "",
    val lastName: String = "",
    val nationalId: String = "",
    val phoneNumber: String = "",
    val residentialAddress: String = ""
)

data class HardwareScan(
    val imei: String = "",
    val barcode: String = "",
    val capturedAtEpochMillis: Long? = null
)

enum class PaymentPlan(
    val label: String,
    val termMonths: Int,
    val requiredDownPaymentCents: Long,
    val monthlyPaymentCents: Long
) {
    STARTER("Starter 6 months", 6, 7_500, 18_500),
    FLEX("Flex 9 months", 9, 10_000, 13_900),
    VALUE("Value 12 months", 12, 12_500, 10_900)
}

data class EnrollmentPayload(
    val kycData: KycData,
    val hardwareScan: HardwareScan,
    val selectedPlan: PaymentPlan,
    val downPaymentCents: Long,
    val agentId: String
)

