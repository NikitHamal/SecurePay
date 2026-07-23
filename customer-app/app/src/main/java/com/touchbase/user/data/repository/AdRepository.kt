package com.touchbase.user.data.repository

import com.touchbase.user.data.model.AdModel
import com.touchbase.user.data.remote.SecurePayApi
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdRepository(private val api: SecurePayApi) {

    suspend fun getActiveAds(): Result<List<AdModel>> = withContext(Dispatchers.IO) {
        runCatching {
            SecureLog.i("AdRepository", "Fetching active ads from API")
            val response = api.getAds(active = true)
            if (!response.success) {
                throw Exception(response.message ?: "Failed to fetch ads")
            }
            response.ads.filter { it.isActive }
                .sortedBy { it.order }
                .take(3)
        }.onFailure { e ->
            SecureLog.e("AdRepository", "Failed to fetch ads", e)
        }
    }

    suspend fun getAdById(adId: String): Result<AdModel?> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getAds(active = true)
            response.ads.find { it.id == adId }
        }.onFailure { e ->
            SecureLog.e("AdRepository", "Failed to fetch ad $adId", e)
        }
    }

    fun shouldShowAds(permissionsReady: Boolean): Boolean {
        return !permissionsReady
    }
}
