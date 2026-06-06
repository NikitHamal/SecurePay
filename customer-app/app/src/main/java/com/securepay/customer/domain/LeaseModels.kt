package com.securepay.customer.domain

import kotlin.math.roundToInt

private const val WARNING_WINDOW_MILLIS = 10 * 60 * 1000L

data class DeviceLeaseSnapshot(
    val dueAtEpochMillis: Long,
    val remainingBalanceCents: Long,
    val totalLoanCents: Long
)

data class DeviceLeaseUiState(
    val status: DeviceStatus,
    val remainingMillis: Long,
    val dueAtEpochMillis: Long,
    val remainingBalanceCents: Long,
    val totalLoanCents: Long,
    val lastPolicyResult: PolicyActionResult? = null
) {
    val paymentProgress: Float
        get() {
            if (totalLoanCents <= 0L) return 1f
            val paid = totalLoanCents - remainingBalanceCents
            return (paid.toFloat() / totalLoanCents.toFloat()).coerceIn(0f, 1f)
        }

    val paidPercent: Int
        get() = (paymentProgress * 100f).roundToInt()

    val formattedBalance: String
        get() = "KES %,d".format(remainingBalanceCents / 100)

    val countdownText: String
        get() {
            if (remainingMillis <= 0L) return "00:00:00"
            val totalSeconds = remainingMillis / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return "%02d:%02d:%02d".format(hours, minutes, seconds)
        }
}

data class PolicyActionResult(
    val lockTaskAttempted: Boolean,
    val adbDisableAttempted: Boolean,
    val forceLockAttempted: Boolean,
    val deviceOwnerMode: Boolean,
    val message: String
)

fun DeviceLeaseSnapshot.evaluate(nowEpochMillis: Long): DeviceLeaseUiState {
    val remainingMillis = dueAtEpochMillis - nowEpochMillis
    val status = when {
        remainingMillis <= 0L -> DeviceStatus.LOCKED
        remainingMillis <= WARNING_WINDOW_MILLIS -> DeviceStatus.WARNING
        else -> DeviceStatus.ACTIVE
    }

    return DeviceLeaseUiState(
        status = status,
        remainingMillis = remainingMillis.coerceAtLeast(0L),
        dueAtEpochMillis = dueAtEpochMillis,
        remainingBalanceCents = remainingBalanceCents,
        totalLoanCents = totalLoanCents
    )
}
