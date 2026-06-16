package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerateQrRequest(
    val imei: String,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null
)

@Serializable
data class ProvisioningQrResponse(
    val token: String = "",
    val activationCode: String = "",
    val qrPayload: String = "",
    val expiresAt: Long = 0L,
    val account: ProvisioningAccountSummary = ProvisioningAccountSummary(),
    val device: ProvisioningDeviceSummary = ProvisioningDeviceSummary()
)

@Serializable
data class ProvisioningAccountSummary(
    val id: String = "",
    val customerName: String = ""
)

@Serializable
data class ProvisioningDeviceSummary(
    val imei: String = "",
    val model: String = ""
)

@Serializable
data class ProvisioningStatusResponse(
    val token: String = "",
    val activationCode: String = "",
    val status: String = "pending",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val provisionedAt: Long? = null,
    val activatedAt: Long? = null,
    val account: ProvisioningAccountSummary = ProvisioningAccountSummary(),
    val device: ProvisioningDeviceSummary = ProvisioningDeviceSummary()
)
