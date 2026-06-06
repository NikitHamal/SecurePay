package com.securepay.agent.data.repository

import com.securepay.agent.data.model.EnrollmentData
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.random.Random

/**
 * Result of submitting an enrollment to the SecurePay backend.
 */
data class EnrollmentResult(
    val success: Boolean,
    val enrollmentId: String,
    val message: String
)

/**
 * Repository responsible for persisting completed enrollments. The network call
 * is mocked with a delay; in production this would POST to the SecurePay
 * provisioning API which returns a server-generated enrollment id.
 */
class EnrollmentRepository {

    suspend fun submitEnrollment(data: EnrollmentData): EnrollmentResult {
        // Simulate network latency.
        delay(1500)

        val enrollmentId = generateEnrollmentId()
        return EnrollmentResult(
            success = true,
            enrollmentId = enrollmentId,
            message = "Enrollment for ${data.customerName} registered successfully."
        )
    }

    private fun generateEnrollmentId(): String {
        val suffix = (1..6)
            .map { Random.nextInt(0, 36) }
            .joinToString("") { it.toString(36) }
            .uppercase(Locale.US)
        return "SP-$suffix"
    }
}
