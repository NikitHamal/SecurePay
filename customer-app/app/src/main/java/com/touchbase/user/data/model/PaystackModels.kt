package com.touchbase.user.data.model

import kotlinx.serialization.Serializable

/** Providers Paystack supports for Ghana mobile money. */
enum class MomoProvider(val key: String, val display: String) {
    MTN("mtn", "MTN MoMo"),
    VODAFONE("vod", "Vodafone Cash"),
    TELECEL("tgo", "Telecel Cash");

    companion object {
        fun fromKey(key: String): MomoProvider =
            entries.firstOrNull { it.key == key.lowercase() } ?: MTN
    }
}

@Serializable
data class InitializePaystackRequest(
    val accountId: String,
    val imei: String,
    /** Amount in GHS (e.g. 2.50 for GH₵2.50). The server converts to pesewas. */
    val amount: Double,
    val phone: String,
    val provider: String
)

@Serializable
data class InitializePaystackResponse(
    val ok: Boolean = false,
    val reference: String = "",
    val accessCode: String? = null,
    val status: String = "",
    val displayText: String = "",
    val customerEmail: String = "",
    /** Amount in pesewas. */
    val amount: Int = 0,
    val provider: String = "",
    val phone: String = "",
    val otpRequired: Boolean = false
)

@Serializable
data class SubmitOtpRequest(
    val reference: String,
    val otp: String,
    val accountId: String,
    val imei: String
)

@Serializable
data class SubmitOtpResponse(
    val ok: Boolean = false,
    val status: String = "",
    val message: String? = null,
    val otpRequired: Boolean = false,
    val reference: String = ""
)

@Serializable
data class VerifyPaystackResponse(
    val ok: Boolean = false,
    val reference: String = "",
    val status: String = "",
    val amount: Int? = null,
    val currency: String? = null,
    val channel: String? = null,
    val paidAt: String? = null,
    val applied: Boolean = false,
    val paidOff: Boolean = false,
    val newAmountPaid: Int? = null,
    val nextPaymentDueEpochMillis: Long? = null,
    val lockedByDealer: Boolean? = null
)
