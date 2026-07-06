package com.touchbase.user.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationSample(
    val lat: Double,
    val lng: Double,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val timestamp: Long
)

@Serializable
data class LocationReportRequest(
    val accountId: String,
    val imei: String,
    val logs: List<LocationSample> = emptyList(),
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val timestamp: Long? = null
)
