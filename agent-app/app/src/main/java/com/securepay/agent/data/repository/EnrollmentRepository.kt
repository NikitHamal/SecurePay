package com.securepay.agent.data.repository

import com.securepay.agent.data.model.CustomerEnrollment
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Submits completed enrollments to the SecurePay backend.
 */
interface EnrollmentRepository {
    /**
     * Transmits a fully assembled [CustomerEnrollment].
     *
     * @return [Result.success] with the server-assigned enrollment id on success,
     *         or [Result.failure] when submission fails.
     */
    suspend fun submitEnrollment(enrollment: CustomerEnrollment): Result<String>
}

/**
 * In-memory mock that simulates a network round trip. Useful for offline field
 * demos and for development before the real API is wired in.
 */
class MockEnrollmentRepository(
    private val networkDelayMillis: Long = 1500L
) : EnrollmentRepository {

    override suspend fun submitEnrollment(enrollment: CustomerEnrollment): Result<String> {
        delay(networkDelayMillis)

        // Basic server-side style guard so the happy path is deterministic.
        if (enrollment.imei.length != IMEI_LENGTH) {
            return Result.failure(
                IllegalArgumentException("Invalid IMEI: expected $IMEI_LENGTH digits")
            )
        }
        if (enrollment.customerName.isBlank()) {
            return Result.failure(IllegalArgumentException("Customer name is required"))
        }

        val enrollmentId = "ENR-" + UUID.randomUUID().toString().take(8).uppercase()
        return Result.success(enrollmentId)
    }

    private companion object {
        const val IMEI_LENGTH = 15
    }
}
