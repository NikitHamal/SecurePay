package com.touchbase.user.data.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

enum class DeviceStatus {
    ACTIVE,
    WARNING,
    LOCKED;

    companion object {
        val WARNING_WINDOW: Duration = 24.hours

        fun evaluate(nextPaymentDueEpochMillis: Long, lockedByDealer: Boolean, nowEpochMillis: Long): DeviceStatus {
            if (lockedByDealer) return LOCKED
            val remainingMillis = nextPaymentDueEpochMillis - nowEpochMillis
            return when {
                remainingMillis <= 0L -> LOCKED
                remainingMillis <= WARNING_WINDOW.inWholeMilliseconds -> WARNING
                else -> ACTIVE
            }
        }
    }
}
