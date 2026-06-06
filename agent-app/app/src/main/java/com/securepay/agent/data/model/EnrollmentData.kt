package com.securepay.agent.data.model

import kotlin.math.roundToInt

/**
 * Available installment financing plans. Each plan defines the number of
 * months over which the financed amount is repaid.
 */
enum class FinancePlan(val months: Int, val label: String) {
    PLAN_3M(3, "3 Months"),
    PLAN_6M(6, "6 Months"),
    PLAN_12M(12, "12 Months");

    companion object {
        val DEFAULT: FinancePlan = PLAN_6M
    }
}

/**
 * Immutable snapshot of a single customer enrollment. The financed amount and
 * daily installment are derived rather than stored so the values can never drift
 * out of sync with the underlying loan inputs.
 *
 * This maps directly onto the shared EnrollmentData contract consumed by the
 * customer-app and dealer-dashboard.
 */
data class EnrollmentData(
    val customerName: String = "",
    val nationalId: String = "",
    val phoneNumber: String = "",
    val imei: String = "",
    val deviceModel: String = "",
    val totalLoanAmount: Double = 0.0,
    val downPayment: Double = 0.0,
    val planMonths: Int = FinancePlan.DEFAULT.months,
    val status: DeviceStatus = DeviceStatus.ACTIVE
) {
    /** Principal that is actually financed after the down payment. */
    val financedAmount: Double
        get() = (totalLoanAmount - downPayment).coerceAtLeast(0.0)

    /** Total number of days in the repayment schedule (30 days per plan month). */
    val totalDays: Int
        get() = planMonths * 30

    /** Per-day repayment, derived from the financed amount over the plan term. */
    val dailyInstallment: Double
        get() = if (totalDays > 0) {
            ((financedAmount / totalDays) * 100).roundToInt() / 100.0
        } else {
            0.0
        }
}
