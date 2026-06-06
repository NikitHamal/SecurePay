package com.securepay.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Payment(
    val id: String = "",
    val accountId: String = "",
    val amount: Int = 0,
    val method: String = "",
    val reference: String? = null,
    val recordedBy: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class RecordPaymentRequest(
    val accountId: String,
    val amount: Int,
    val method: String,
    val reference: String? = null
)

@Serializable
data class RecordPaymentResponse(
    val payment: Payment,
    val account: Account
)