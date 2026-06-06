package com.securepay.agent.data.model

/**
 * Lifecycle status of an enrolled SecurePay account.
 *
 * This enum is intentionally field-identical across the customer app,
 * dealer dashboard and this field-sales agent app.
 */
enum class EnrollmentStatus {
    ACTIVE,
    WARNING,
    LOCKED
}
