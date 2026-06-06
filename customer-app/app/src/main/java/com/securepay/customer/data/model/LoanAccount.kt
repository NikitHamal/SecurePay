package com.securepay.customer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceCheckResponse(
    val enrolled: Boolean = false,
    val device: DeviceInfo? = null,
    val account: AccountBrief? = null
)

@Serializable
data class DeviceInfo(
    val id: String = "",
    val imei: String = "",
    val model: String = "",
    val status: String = ""
)

@Serializable
data class AccountBrief(
    val id: String = "",
    val customerName: String = "",
    val status: String = "",
    val nextPaymentDue: Long = 0L,
    val amountPaid: Int = 0,
    val totalLoanAmount: Int = 0,
    val dailyRate: Int = 0
)

@Serializable
data class AccountResponse(
    val id: String = "",
    val customerName: String = "",
    val nationalId: String = "",
    val phoneNumber: String = "",
    val imei: String = "",
    val deviceModel: String = "",
    val planName: String = "",
    val totalLoanAmount: Int = 0,
    val amountPaid: Int = 0,
    val remainingBalance: Int = 0,
    val dailyRate: Int = 0,
    val nextPaymentDueEpochMillis: Long = 0L,
    val status: String = "ACTIVE",
    val lockedByDealer: Int = 0,
    val downPayment: Int = 0,
    val termDays: Int = 0,
    val currencyCode: String = "KES",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class LoanAccount(
    val id: String,
    val customerName: String,
    val imei: String,
    val deviceModel: String,
    val planName: String,
    val totalLoanAmountCents: Int,
    val amountPaidCents: Int,
    val dailyRateCents: Int,
    val termDays: Int,
    val nextPaymentDueEpochMillis: Long,
    val lockedByDealer: Boolean,
    val currencyCode: String = "KES"
) {
    val remainingBalanceCents: Int
        get() = (totalLoanAmountCents - amountPaidCents).coerceAtLeast(0)

    val repaymentProgress: Float
        get() = if (totalLoanAmountCents <= 0) 1f
        else (amountPaidCents.toFloat() / totalLoanAmountCents).coerceIn(0f, 1f)

    val displayStatus: DeviceStatus
        get() = DeviceStatus.evaluate(nextPaymentDueEpochMillis, lockedByDealer, System.currentTimeMillis())
}

fun formatCentsAsCurrency(cents: Int, currencyCode: String = "KES"): String {
    val whole = cents / 100.0
    return if (currencyCode == "KES") {
        "KES ${String.format("%,.0f", whole)}"
    } else {
        val symbol = when (currencyCode) {
            "USD" -> "$"
            "EUR" -> "\u20ac"
            "GBP" -> "\u00a3"
            else -> "$currencyCode "
        }
        "$symbol${String.format("%,.2f", whole)}"
    }
}