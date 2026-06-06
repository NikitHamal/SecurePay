package com.securepay.customer.domain

import com.securepay.customer.data.model.DeviceStatus
import java.util.concurrent.TimeUnit

/**
 * Pure, side-effect-free logic for evaluating device state and formatting the
 * live countdown. Kept free of Android dependencies so it can be unit-tested
 * and reused identically across apps in the ecosystem.
 */
object CountdownEngine {

    /** Default warning window before the deadline (24 hours). */
    const val WARNING_THRESHOLD_MILLIS: Long = 24L * 60L * 60L * 1000L

    /**
     * Evaluate the device status against a live clock.
     *
     * Rules (shared across the ecosystem):
     *  - LOCKED  when now >= nextDue
     *  - WARNING when (nextDue - now) <= warningThreshold
     *  - ACTIVE  otherwise
     */
    fun evaluateStatus(
        nowMillis: Long,
        nextDueMillis: Long,
        warningThreshold: Long = WARNING_THRESHOLD_MILLIS
    ): DeviceStatus {
        val remaining = nextDueMillis - nowMillis
        return when {
            remaining <= 0L -> DeviceStatus.LOCKED
            remaining <= warningThreshold -> DeviceStatus.WARNING
            else -> DeviceStatus.ACTIVE
        }
    }

    /**
     * Format the time remaining as "HH:MM:SS", or "Overdue" once the deadline
     * has passed. Hours are not capped at 24 so multi-day windows read clearly.
     */
    fun formatRemaining(millis: Long): String {
        if (millis <= 0L) return "Overdue"
        val totalSeconds = millis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Progress of the current countdown window expressed as a fraction in
     * 0f..1f, where 1f means the full window remains and 0f means it has
     * elapsed. The window length defaults to the warning threshold so the
     * indicator drains as the deadline nears.
     */
    fun progressFraction(
        nowMillis: Long,
        nextDueMillis: Long,
        windowMillis: Long = WARNING_THRESHOLD_MILLIS
    ): Float {
        if (windowMillis <= 0L) return 0f
        val remaining = nextDueMillis - nowMillis
        return (remaining.toFloat() / windowMillis.toFloat()).coerceIn(0f, 1f)
    }

    /** Convenience: whole hours remaining (never negative). */
    fun hoursRemaining(millis: Long): Long =
        if (millis <= 0L) 0L else TimeUnit.MILLISECONDS.toHours(millis)
}
