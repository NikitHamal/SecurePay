package com.securepay.customer.data.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The reactive financing state of the device. The DPC enforces restrictions
 * strictly as a function of how much time remains before the next payment is due.
 *
 *  - [ACTIVE]  : balance current, full device functionality.
 *  - [WARNING] : payment due soon (within [WARNING_WINDOW]); the user is nudged.
 *  - [LOCKED]  : payment overdue; the full-screen lock overlay is enforced.
 */
enum class DeviceStatus {
    ACTIVE,
    WARNING,
    LOCKED;

    companion object {
        /** How long before the deadline the device starts surfacing warnings. */
        val WARNING_WINDOW: Duration = 24.hours

        /**
         * Pure evaluation of state from a deadline and the current clock.
         * Kept side-effect free so it can be unit tested and reused by the
         * reactive countdown without touching the Android framework.
         */
        fun evaluate(nextPaymentDueEpochMillis: Long, nowEpochMillis: Long): DeviceStatus {
            val remainingMillis = nextPaymentDueEpochMillis - nowEpochMillis
            return when {
                remainingMillis <= 0L -> LOCKED
                remainingMillis <= WARNING_WINDOW.inWholeMilliseconds -> WARNING
                else -> ACTIVE
            }
        }
    }
}
