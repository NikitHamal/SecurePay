package com.touchbase.user.data.model

import kotlinx.serialization.Serializable

/**
 * Model for advertisements to be displayed in the app.
 * Ads are fetched from touchbasedata.com and managed via the dashboard.
 */
@Serializable
data class AdModel(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val isActive: Boolean = true,
    val order: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * Response model for fetching multiple ads.
 */
@Serializable
data class AdsResponse(
    val success: Boolean,
    val ads: List<AdModel> = emptyList(),
    val message: String? = null
)

/**
 * Response model for a single ad.
 */
@Serializable
data class AdResponse(
    val success: Boolean,
    val ad: AdModel? = null,
    val message: String? = null
)
