package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationResponse(
    val available: Boolean = true,
    val accountId: String? = null,
    val isStolen: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val batteryLevel: Int? = null,
    val timestamp: Long? = null,
    val message: String? = null
) {
    val resolvedLatitude: Double? get() = latitude ?: lat
    val resolvedLongitude: Double? get() = longitude ?: lng
    val resolvedBattery: Int? get() = battery ?: batteryLevel
    val hasCoordinates: Boolean get() = available && resolvedLatitude != null && resolvedLongitude != null
}
