package com.securepay.customer.data.remote

import com.securepay.customer.data.model.DeviceStatus
import com.securepay.customer.data.model.LoanState
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * In-memory mock of the SecurePay financing backend. Holds a single seeded
 * [LoanState] and simulates network latency. The deadline is seeded a few
 * minutes out so the live countdown (and the WARNING -> LOCKED transition) is
 * easy to demonstrate without waiting a full day.
 */
class MockApi {

    /**
     * Default demo deadline offset: 3 minutes from "now". Short enough that the
     * countdown ticks visibly toward LOCKED, and (being under the 24h warning
     * threshold) the device seeds in WARNING so the UI is immediately lively.
     */
    private val demoDeadlineOffsetMillis: Long = 3L * 60L * 1000L

    /** Each simulated payment pushes the deadline forward by one day. */
    private val paymentExtensionMillis: Long = 24L * 60L * 60L * 1000L

    /** Each grace request extends the deadline by 5 minutes. */
    private val graceExtensionMillis: Long = 5L * 60L * 1000L

    @Volatile
    private var current: LoanState = seedInitialState()

    /** Build the seeded loan with a demonstrable, near-term deadline. */
    private fun seedInitialState(): LoanState {
        val now = System.currentTimeMillis()
        val total = 45000.0
        val paid = 18250.0
        val nextDue = now + demoDeadlineOffsetMillis
        return LoanState(
            customerId = "CUST-002931",
            customerName = "Amara Okeke",
            deviceModel = "Tecno Spark 20 Pro",
            imei = "356938035643809",
            totalLoanAmount = total,
            amountPaid = paid,
            remainingBalance = total - paid,
            dailyInstallment = 350.0,
            serverEpochMillis = now,
            nextDueEpochMillis = nextDue,
            // Initial server-reported status; the live ticker re-evaluates this
            // client-side via CountdownEngine each second.
            status = if (nextDue - now <= 24L * 60L * 60L * 1000L) {
                DeviceStatus.WARNING
            } else {
                DeviceStatus.ACTIVE
            }
        )
    }

    /** Fetch the current loan state, refreshing the server clock to "now". */
    suspend fun fetchLoanState(): LoanState {
        delay(400) // simulate network latency
        val now = System.currentTimeMillis()
        current = current.copy(serverEpochMillis = now)
        return current
    }

    /**
     * Simulate a successful payment integration: reduce the remaining balance
     * by one daily installment (never below zero), increase amount paid, and
     * push the deadline forward by a day.
     */
    suspend fun simulatePayment(): LoanState {
        delay(600) // simulate payment-gateway round trip
        val now = System.currentTimeMillis()
        val payment = min(current.dailyInstallment, current.remainingBalance)
        val newPaid = current.amountPaid + payment
        val newRemaining = (current.totalLoanAmount - newPaid).coerceAtLeast(0.0)
        // Extend from whichever is later: the existing deadline or now.
        val base = maxOf(current.nextDueEpochMillis, now)
        val newDue = base + paymentExtensionMillis
        current = current.copy(
            amountPaid = newPaid,
            remainingBalance = newRemaining,
            serverEpochMillis = now,
            nextDueEpochMillis = newDue,
            status = DeviceStatus.ACTIVE
        )
        return current
    }

    /**
     * Grant a short grace window, pushing the deadline 5 minutes forward from
     * now. Used by the lock overlay's "Request 5-Minute Grace Window" action.
     */
    suspend fun requestGrace(): LoanState {
        delay(300)
        val now = System.currentTimeMillis()
        val newDue = now + graceExtensionMillis
        current = current.copy(
            serverEpochMillis = now,
            nextDueEpochMillis = newDue,
            status = DeviceStatus.WARNING
        )
        return current
    }
}
