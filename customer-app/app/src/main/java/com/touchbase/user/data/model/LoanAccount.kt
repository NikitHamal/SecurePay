package com.touchbase.user.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceCheckResponse(
    val enrolled: Boolean = false,
    val device: DeviceInfo? = null,
    val account: AccountBrief? = null,
    val securityPolicy: DeviceSecurityPolicy = DeviceSecurityPolicy(),
    val serverTime: Long = 0L
)

@Serializable
data class ActivateResponse(
    val enrolled: Boolean = false,
    val activated: Boolean = false,
    val imei: String = "",
    val apiSecret: String = "",
    val device: DeviceInfo? = null,
    val account: AccountBrief? = null,
    val securityPolicy: DeviceSecurityPolicy = DeviceSecurityPolicy(),
    val serverTime: Long = 0L
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
    val dailyRate: Int = 0,
    val releaseApproved: Boolean = false,
    val releaseApprovedAt: Long? = null,
    val releasedAt: Long? = null
)

@Serializable
data class DeviceSecurityPolicy(
    val version: Long = 0L,
    val frpEnabled: Boolean = false,
    val frpAccountIds: List<String> = emptyList(),
    val blockFactoryReset: Boolean = true,
    val blockSafeBoot: Boolean = true,
    val blockDeveloperOptions: Boolean = true,
    val blockUnknownSources: Boolean = true,
    val blockAccountModification: Boolean = true
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
    val currencyCode: String = "GHS",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val releaseApproved: Boolean = false,
    val releaseApprovedAt: Long? = null,
    val releasedAt: Long? = null,
    val securityPolicy: DeviceSecurityPolicy = DeviceSecurityPolicy(),
    val serverTime: Long = 0L
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
    val currencyCode: String = "GHS",
    val releaseApproved: Boolean = false,
    val releaseApprovedAt: Long? = null,
    val releasedAt: Long? = null,
    val securityPolicy: DeviceSecurityPolicy = DeviceSecurityPolicy()
) {
    val remainingBalanceCents: Int
        get() = (totalLoanAmountCents - amountPaidCents).coerceAtLeast(0)

    val repaymentProgress: Float
        get() = if (totalLoanAmountCents <= 0) 1f
        else (amountPaidCents.toFloat() / totalLoanAmountCents).coerceIn(0f, 1f)
}

@Serializable
data class PaymentEntry(
    val id: String = "",
    val accountId: String = "",
    val amount: Int = 0,
    val method: String = "",
    val reference: String? = null,
    val createdAt: Long = 0L
)

@Serializable
data class PaymentsResponse(
    val payments: List<PaymentEntry> = emptyList(),
    val serverTime: Long = 0L
)

fun formatCentsAsCurrency(cents: Int, currencyCode: String = "GHS"): String {
    val whole = cents / 100.0
    return if (currencyCode == "GHS") {
        "GH₵ ${String.format("%,.2f", whole)}"
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


@kotlinx.serialization.Serializable
data class ReleaseCompleteResponse(
    val ok: Boolean = false,
    val releasedAt: Long = 0L,
    val serverTime: Long = 0L
)

@kotlinx.serialization.Serializable
data class AppUpdateResponse(
    val available: Boolean = false,
    val url: String = "",
    val sha256Base64: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val minSupportedVersionCode: Int = 0,
    val serverTime: Long = 0L
)
