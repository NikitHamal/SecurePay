package com.securepay.customer.data.remote

import com.securepay.customer.data.model.LoanAccount
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Stand-in for the SecurePay backend. It seeds a single financed account whose
 * deadline is anchored to the wall clock at process start, so the on-device
 * countdown ticks against a realistic "API epoch timestamp".
 *
 * All mutating calls are suspend functions with a small simulated latency to
 * mirror the eventual Retrofit implementation.
 */
class MockSecurePayApi {

    @Volatile
    private var account: LoanAccount = seedAccount()

    private fun seedAccount(): LoanAccount {
        // Deadline set a short way out so WARNING/LOCKED transitions are observable
        // during a demo, while still exercising the ACTIVE path on first launch.
        val now = System.currentTimeMillis()
        return LoanAccount(
            id = "ACC-100245",
            customerName = "Amara Okonkwo",
            imei = "356938035643809",
            deviceModel = "SecurePay X20",
            planName = "Standard 90-Day",
            totalLoanAmount = 480.0,
            amountPaid = 312.5,
            dailyRate = 5.33,
            termDays = 90,
            nextPaymentDueEpochMillis = now + 26.minutes.inWholeMilliseconds
        )
    }

    suspend fun fetchAccount(): LoanAccount {
        delay(450)
        return account
    }

    /**
     * Simulates a successful payment: credits the daily installment and extends
     * the deadline by one billing day. Returns the refreshed account.
     */
    suspend fun simulatePayment(): LoanAccount {
        delay(900)
        val current = account
        val updated = current.copy(
            amountPaid = (current.amountPaid + current.dailyRate)
                .coerceAtMost(current.totalLoanAmount),
            nextPaymentDueEpochMillis = maxOf(
                current.nextPaymentDueEpochMillis,
                System.currentTimeMillis()
            ) + 1.days.inWholeMilliseconds
        )
        account = updated
        return updated
    }

    /**
     * Grants a fixed 5-minute grace window from the locked overlay. Returns the
     * refreshed account whose deadline now sits just in the future.
     */
    suspend fun requestGraceWindow(): LoanAccount {
        delay(600)
        val current = account
        val updated = current.copy(
            nextPaymentDueEpochMillis = System.currentTimeMillis() +
                5.minutes.inWholeMilliseconds
        )
        account = updated
        return updated
    }
}
