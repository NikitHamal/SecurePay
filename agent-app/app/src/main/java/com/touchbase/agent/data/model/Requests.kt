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
