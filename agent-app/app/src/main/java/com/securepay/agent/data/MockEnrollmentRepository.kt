package com.securepay.agent.data

import com.securepay.agent.domain.EnrollmentPayload
import kotlinx.coroutines.delay

class MockEnrollmentRepository {
    suspend fun submitEnrollment(payload: EnrollmentPayload): Result<String> {
        delay(850)
        return if (payload.hardwareScan.imei.isBlank()) {
            Result.failure(IllegalStateException("IMEI capture is required"))
        } else {
            Result.success("SP-ENR-${payload.hardwareScan.imei.takeLast(6)}")
        }
    }
}

