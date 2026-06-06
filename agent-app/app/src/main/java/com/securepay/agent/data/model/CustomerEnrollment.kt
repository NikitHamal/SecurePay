package com.securepay.agent.data.model

/**
 * Immutable, network-ready representation of a customer enrollment.
 *
 * The field set is identical across all three SecurePay apps. Defaults make it
 * safe to use as the in-progress draft inside the enrollment wizard before the
 * agent has filled every field.
 */
data class CustomerEnrollment(
    val id: String = "",
    val customerName: String = "",
    val nationalId: String = "",
    val phoneNumber: String = "",
    val imei: String = "",
    val deviceModel: String = "",
    val planName: String = "",
    val totalLoanAmount: Double = 0.0,
    val downPayment: Double = 0.0,
    val dailyRate: Double = 0.0,
    val termDays: Int = 0,
    val status: EnrollmentStatus = EnrollmentStatus.ACTIVE
)
