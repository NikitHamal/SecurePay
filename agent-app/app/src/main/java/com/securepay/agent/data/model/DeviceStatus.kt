package com.securepay.agent.data.model

/**
 * Lifecycle state of a financed device. Mirrors the shared domain model used by
 * the SecurePay customer-app and dealer-dashboard.
 */
enum class DeviceStatus {
    ACTIVE,
    WARNING,
    LOCKED
}
