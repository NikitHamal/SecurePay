package com.touchbase.user.domain

import java.util.Locale
import kotlin.math.max

/**
 * Value object describing how much time is left before the next payment deadline.
 * Produced fresh on every reactive tick from the difference between the deadline
 * and the current clock.
 */
data class RemainingTime(
    val totalMillis: Long
) {
    val isExpired: Boolean get() = totalMillis <= 0L

    private val clamped: Long get() = max(0L, totalMillis)

    val days: Long get() = clamped / MILLIS_PER_DAY
    val hours: Long get() = (clamped % MILLIS_PER_DAY) / MILLIS_PER_HOUR
    val minutes: Long get() = (clamped % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
    val seconds: Long get() = (clamped % MILLIS_PER_MINUTE) / 1000L

    /** Compact label e.g. "1d 04:09:57" or "00:26:00" when under a day. */
    fun format(): String {
        if (isExpired) return "OVERDUE"
        return if (days > 0) {
            String.format(Locale.US, "%dd %02d:%02d:%02d", days, hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
        private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

        fun until(deadlineEpochMillis: Long, nowEpochMillis: Long): RemainingTime =
            RemainingTime(deadlineEpochMillis - nowEpochMillis)
    }
}
