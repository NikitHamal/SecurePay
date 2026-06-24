package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountStatus {
    ACTIVE, WARNING, LOCKED
}

@Serializable
data class Account(
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
    val status: AccountStatus = AccountStatus.ACTIVE,
    val lockedByDealer: Int = 0,
    val downPayment: Int = 0,
    val termDays: Int = 0,
    val currencyCode: String = "GH₵",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val releaseApproved: Boolean = false,
    val releaseApprovedAt: Long? = null,
    val releasedAt: Long? = null,
    val customerPhotoPath: String? = null,
    val nationalIdFrontPath: String? = null,
    val nationalIdBackPath: String? = null
)

fun Account.isLocked(): Boolean = status == AccountStatus.LOCKED
fun Account.isWarning(): Boolean = status == AccountStatus.WARNING
fun Account.isActive(): Boolean = status == AccountStatus.ACTIVE

fun Account.displayStatus(): String = if (releaseApproved) "Release approved" else when (status) {
    AccountStatus.ACTIVE -> "Active"
    AccountStatus.WARNING -> "Warning"
    AccountStatus.LOCKED -> "Locked"
}

fun formatAmount(cents: Int): String {
    val amount = cents / 100.0
    return "GH₵ ${String.format("%,.0f", amount)}"
}

fun Account.formatDailyRate(): String = formatAmount(dailyRate)

fun Account.daysUntilDue(now: Long = System.currentTimeMillis()): Long {
    val diff = nextPaymentDueEpochMillis - now
    return if (diff <= 0) 0 else diff / (24 * 60 * 60 * 1000)
}

fun Account.hoursUntilDue(now: Long = System.currentTimeMillis()): Long {
    val diff = nextPaymentDueEpochMillis - now
    return if (diff <= 0) 0 else diff / (60 * 60 * 1000)
}

fun computeStatus(nextPaymentDueEpochMillis: Long, lockedByDealer: Int, now: Long = System.currentTimeMillis()): AccountStatus {
    if (lockedByDealer == 1) return AccountStatus.LOCKED
    if (now >= nextPaymentDueEpochMillis) return AccountStatus.LOCKED
    if (nextPaymentDueEpochMillis - now <= 24 * 60 * 60 * 1000) return AccountStatus.WARNING
    return AccountStatus.ACTIVE
}
