package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountRequest(
    val customerName: String,
    val nationalId: String,
    val phoneNumber: String,
    val imei: String,
    val planId: String,
    val downPayment: Int? = null
)

@Serializable
data class AddDeviceRequest(
    val imei: String,
    val model: String
)
