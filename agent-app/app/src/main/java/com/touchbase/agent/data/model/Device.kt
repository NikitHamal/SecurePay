package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String = "",
    val imei: String = "",
    val model: String = "",
    val dealerId: String = "",
    val status: String = "in_stock",
    val createdAt: Long = 0L,
    val customerName: String? = null,
    val soldAt: Long? = null,
    /** Who/where this phone was registered (anti-fraud context, when captured). */
    val registeredByName: String? = null,
    val registrationLat: Double? = null,
    val registrationLng: Double? = null,
    val registrationAccuracy: Float? = null
)

@Serializable
data class DeviceCheckResponse(
    val enrolled: Boolean = false,
    val device: DeviceInfo? = null,
    val account: AccountBrief? = null
)

@Serializable
data class DeviceInfo(
    val id: String = "",
    val imei: String = "",
    val model: String = "",
    val status: String = ""
)

@Serializable
data class AccountBrief(
    val id: String = "",
    val customerName: String = "",
    val status: String = "",
    val nextPaymentDue: Long = 0L,
    val amountPaid: Int = 0,
    val totalLoanAmount: Int = 0,
    val dailyRate: Int = 0
)
