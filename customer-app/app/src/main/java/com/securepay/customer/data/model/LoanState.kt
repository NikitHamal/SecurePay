package com.securepay.customer.data.model

/**
 * Immutable snapshot of a financed device's loan and timing state.
 *
 * Shared domain model — field names and types MUST match the agent-app and
 * dealer-dashboard exactly.
 *
 * @property serverEpochMillis the mock API's notion of "now" at fetch time.
 * @property nextDueEpochMillis the absolute deadline for the next installment.
 */
data class LoanState(
    val customerId: String,
    val customerName: String,
    val deviceModel: String,
    val imei: String,
    val totalLoanAmount: Double,
    val amountPaid: Double,
    val remainingBalance: Double,
    val dailyInstallment: Double,
    val serverEpochMillis: Long,
    val nextDueEpochMillis: Long,
    val status: DeviceStatus
) {
    /** Fraction of the total loan that has been repaid, clamped to 0f..1f. */
    val paymentProgress: Float
        get() = if (totalLoanAmount <= 0.0) {
            0f
        } else {
            (amountPaid / totalLoanAmount).toFloat().coerceIn(0f, 1f)
        }

    /** Milliseconds remaining until the deadline relative to the supplied clock. */
    fun millisUntilDue(nowMillis: Long): Long = nextDueEpochMillis - nowMillis

    /** True once the supplied clock has reached or passed the deadline. */
    fun isOverdue(nowMillis: Long): Boolean = nowMillis >= nextDueEpochMillis
}
