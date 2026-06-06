package com.securepay.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String = "",
    val imei: String = "",
    val model: String = "",
    val dealerId: String = "",
    val status: String = "in_stock",
    val createdAt: Long = 0L
)

@Serializable
data class DeviceCheckResponse(
    val enrolled: Boolean,
    val imei: String = "",
    val deviceModel: String? = null,
    val status: String? = null,
    val accountId: String? = null,
    val customerName: String? = null,
    val nextPaymentDue: Long? = null,
    val dailyRate: Int? = null
)