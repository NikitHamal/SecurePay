package com.touchbase.user.data.repository

import com.touchbase.user.data.model.AdModel
import com.touchbase.user.data.model.AdsResponse
import com.touchbase.user.data.remote.SecurePayApi
import com.touchbase.user.admin.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for fetching and managing advertisements.
 * Ads are fetched from touchbasedata.com API.
 */
class AdRepository(private val api: SecurePayApi) {
    
    private val touchBaseDataUrl = "https://touchbasedata.com/api/ads"
    
    /**
     * Fetches active ads from the touchbasedata.com API.
     * Returns up to 3 active ads sorted by order.
     */
    suspend fun getActiveAds(): Result<List<AdModel>> = withContext(Dispatchers.IO) {
        runCatching {
            // In production, this would call the actual API
            // For now, return mock data to demonstrate the functionality
            // TODO: Replace with actual API call when touchbasedata.com endpoint is available
            
            SecureLog.i("AdRepository", "Fetching ads from touchbasedata.com")
            
            // Mock data for demonstration
            // In production: val response = api.getAds()
            val mockAds = listOf(
                AdModel(
                    id = "1",
                    title = "Special Offer",
                    description = "Get 10% discount on your next payment!",
                    imageUrl = null,
                    linkUrl = "https://touchbasedata.com/offer1",
                    isActive = true,
                    order = 1
                ),
                AdModel(
                    id = "2",
                    title = "New Phones Available",
                    description = "Check out our latest phone models",
                    imageUrl = null,
                    linkUrl = "https://touchbasedata.com/phones",
                    isActive = true,
                    order = 2
                ),
                AdModel(
                    id = "3",
                    title = "Extended Warranty",
                    description = "Protect your device with extended warranty",
                    imageUrl = null,
                    linkUrl = "https://touchbasedata.com/warranty",
                    isActive = true,
                    order = 3
                )
            )
            
            // Return only active ads, sorted by order, limited to 3
            mockAds.filter { it.isActive }
                .sortedBy { it.order }
                .take(3)
        }.onFailure { e ->
            SecureLog.e("AdRepository", "Failed to fetch ads", e)
        }
    }
    
    /**
     * Fetches a specific ad by ID.
     */
    suspend fun getAdById(adId: String): Result<AdModel?> = withContext(Dispatchers.IO) {
        runCatching {
            // In production, this would call the actual API
            // For now, return null as we're using mock data
            null
        }.onFailure { e ->
            SecureLog.e("AdRepository", "Failed to fetch ad $adId", e)
        }
    }
    
    /**
     * Checks if ads should be displayed based on permissions.
     * Ads are hidden when device permissions are ready.
     */
    fun shouldShowAds(permissionsReady: Boolean): Boolean {
        // According to client: "That place I said we will remove (the one which is highlighted, don't remove just keep it hidden when permissions are ready)"
        // So ads should be hidden when permissions are ready
        return !permissionsReady
    }
}
