package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountRequest(
    val customerName: String,
    val nationalId: String,
    val phoneNumber: String,
    val imei: String,
    val planId: String? = null,
    val dailyRate: Int? = null,
    val totalAmount: Int? = null,
    val termDays: Int? = null,
    val downPayment: Int? = null,
    val customerPhoto: String? = null,
    val nationalIdFront: String? = null,
    val nationalIdBack: String? = null
)

@Serializable
data class AddDeviceRequest(
    val imei: String,
    val model: String
)

@Serializable
data class UpdateAccountRequest(
    val customerName: String? = null,
    val nationalId: String? = null,
    val phoneNumber: String? = null,
    val dailyRate: Int? = null,
    val totalLoanAmount: Int? = null,
    val termDays: Int? = null,
    val customerPhoto: String? = null,
    val nationalIdFront: String? = null,
    val nationalIdBack: String? = null,
    val isStolen: Boolean? = null
)

@Serializable
data class ReleaseAccountRequest(
    val allowEarlyRelease: Boolean,
    val note: String
)

@kotlinx.serialization.Serializable
data class AppUpdateResponse(
    val available: Boolean = false,
    val url: String = "",
    val sha256Base64: String = "",
    val signatureChecksumBase64: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val minSupportedVersionCode: Int = 0,
    val serverTime: Long = 0L
)
