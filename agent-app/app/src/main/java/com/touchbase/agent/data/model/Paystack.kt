package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PaystackInitializeRequest(
    val accountId: String,
    val amount: Double,
    val phone: String,
    val provider: String = "mtn"
)

@Serializable
data class PaystackInitializeResponse(
    val ok: Boolean = false,
    val reference: String = "",
    val accessCode: String? = null,
    val status: String = "",
    val displayText: String = "",
    val customerEmail: String = "",
    val amount: Int = 0,
    val provider: String = "",
    val phone: String = "",
    val otpRequired: Boolean = false
)

@Serializable
data class PaystackOtpRequest(
    val reference: String,
    val otp: String
)

@Serializable
data class PaystackOtpResponse(
    val ok: Boolean = false,
    val status: String = "",
    val message: String? = null,
    val otpRequired: Boolean = false,
    val reference: String = ""
)

@Serializable
data class PaystackVerifyResponse(
    val ok: Boolean = false,
    val reference: String = "",
    val status: String = "",
    val amount: Int? = null,
    val currency: String? = null,
    val channel: String? = null,
    val paidAt: String? = null,
    val applied: Boolean = false
)
