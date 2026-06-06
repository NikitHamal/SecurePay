package com.securepay.customer.data

import com.securepay.customer.domain.DeviceLease
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MockFinancingRepository {
    private val initialNow = System.currentTimeMillis()
    private val _lease = MutableStateFlow(
        DeviceLease(
            contractId = "SP-CUS-4182",
            customerName = "Amina Okafor",
            imei = "357249105864219",
            activatedEpochMillis = initialNow - DAYS.toMillis(12L),
            dueEpochMillis = initialNow + DAYS.toMillis(3L) + HOURS.toMillis(6L),
            financedAmountCents = 240_000,
            outstandingBalanceCents = 184_500
        )
    )

    fun observeLease(): StateFlow<DeviceLease> = _lease.asStateFlow()

    fun simulatePayment() {
        _lease.update { current ->
            current.copy(
                dueEpochMillis = maxOf(current.dueEpochMillis, System.currentTimeMillis()) + DAYS.toMillis(7L),
                outstandingBalanceCents = (current.outstandingBalanceCents - 12_500).coerceAtLeast(0)
            )
        }
    }

    fun requestGraceWindow() {
        val now = System.currentTimeMillis()
        _lease.update { current ->
            if (current.dueEpochMillis <= now) {
                current.copy(dueEpochMillis = now + MINUTES.toMillis(5L))
            } else {
                current
            }
        }
    }

    private companion object {
        val MINUTES = TimeUnitMillis(60_000)
        val HOURS = TimeUnitMillis(3_600_000)
        val DAYS = TimeUnitMillis(86_400_000)
    }
}

private class TimeUnitMillis(private val multiplier: Long) {
    fun toMillis(value: Long): Long = value * multiplier
}
