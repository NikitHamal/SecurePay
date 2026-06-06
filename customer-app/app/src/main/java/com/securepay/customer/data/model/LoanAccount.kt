package com.securepay.customer.data.model

/**
 * Immutable snapshot of the customer's financing account as returned by the
 * (mock) backend. Field names are aligned with the agent enrollment app and the
 * dealer dashboard so a single record round-trips cleanly across all three tiers.
 */
data class LoanAccount(
    val id: String,
    val customerName: String,
    val imei: String,
    val deviceModel: String,
    val planName: String,
    val totalLoanAmount: Double,
    val amountPaid: Double,
    val dailyRate: Double,
    val termDays: Int,
    /** Epoch millis after which the account is considered overdue / LOCKED. */
    val nextPaymentDueEpochMillis: Long,
    val currencyCode: String = "USD"
) {
    val remainingBalance: Double
        get() = (totalLoanAmount - amountPaid).coerceAtLeast(0.0)

    /** 0f..1f completion of the financing term, used by the progress indicator. */
    val repaymentProgress: Float
        get() = if (totalLoanAmount <= 0.0) 1f
        else (amountPaid / totalLoanAmount).toFloat().coerceIn(0f, 1f)
}
