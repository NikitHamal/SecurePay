package com.securepay.customer.data.model

/**
 * Lifecycle state of a financed device, evaluated against the live ticking clock.
 *
 * Shared domain model — MUST stay in sync with the agent-app and dealer-dashboard.
 */
enum class DeviceStatus {
    /** Payments current; device fully usable. */
    ACTIVE,

    /** Deadline approaching (within the warning threshold). */
    WARNING,

    /** Payment deadline passed; device should be locked by the DPC. */
    LOCKED
}
