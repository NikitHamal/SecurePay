package com.securepay.customer.data

import com.securepay.customer.domain.DeviceLeaseSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class MockLeaseRepository {
    private val _lease = MutableStateFlow(
        DeviceLeaseSnapshot(
            dueAtEpochMillis = System.currentTimeMillis() + 60 * 60 * 1000L,
            remainingBalanceCents = 18_500_00,
            totalLoanCents = 45_000_00
        )
    )

    val lease: StateFlow<DeviceLeaseSnapshot> = _lease

    fun simulatePayment() {
        _lease.update { snapshot ->
            val nextBalance = (snapshot.remainingBalanceCents - 2_500_00).coerceAtLeast(0L)
            snapshot.copy(
                remainingBalanceCents = nextBalance,
                dueAtEpochMillis = snapshot.dueAtEpochMillis + 24 * 60 * 60 * 1000L
            )
        }
    }

    fun requestGraceWindow() {
        _lease.update { snapshot ->
            snapshot.copy(dueAtEpochMillis = System.currentTimeMillis() + 5 * 60 * 1000L)
        }
    }

    fun forceOverdueForTesting() {
        _lease.update { snapshot ->
            snapshot.copy(dueAtEpochMillis = System.currentTimeMillis() - 1000L)
        }
    }
}
