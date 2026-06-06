package com.securepay.customer.domain

data class DeviceLease(
    val contractId: String,
    val customerName: String,
    val imei: String,
    val activatedEpochMillis: Long,
    val dueEpochMillis: Long,
    val financedAmountCents: Long,
    val outstandingBalanceCents: Long
)

