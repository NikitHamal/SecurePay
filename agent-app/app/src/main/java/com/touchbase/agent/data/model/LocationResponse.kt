package com.touchbase.agent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationResponse(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val battery: Int?,
    val timestamp: Long
)
