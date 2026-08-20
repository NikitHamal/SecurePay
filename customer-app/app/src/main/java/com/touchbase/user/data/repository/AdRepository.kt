package com.touchbase.user.data.repository

import android.content.Context
import com.touchbase.user.data.model.AdModel
import com.touchbase.user.data.remote.SecurePayApi
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AdRepository(private val api: SecurePayApi, private val context: Context? = null) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private fun prefs() = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns cached ads instantly if available, then fetches fresh ones in the
     * background. The caller always gets data immediately from cache.
     */
    suspend fun getActiveAds(forceRefresh: Boolean = false): Result<List<AdModel>> = withContext(Dispatchers.IO) {
        // 1. Try cache first
        val cached = loadCachedAds()
        if (!forceRefresh && cached.isNotEmpty()) {
            SecureLog.i(TAG, "Serving ${cached.size} cached ads")
            return@withContext Result.success(cached)
        }

        // 2. Fetch from network
        val result = fetchFromNetwork()
        val ads = result.getOrNull()
        if (ads != null) {
            saveCachedAds(ads)
        }

        // 3. If network failed but we have cache, return cache
        if (ads == null && cached.isNotEmpty()) {
            SecureLog.i(TAG, "Network failed, serving ${cached.size} cached ads")
            return@withContext Result.success(cached)
        }

        result
    }

    /**
     * Returns only cached ads without any network call.
     */
    fun getCachedAds(): List<AdModel> = loadCachedAds()

    /**
     * Forces a network fetch and updates the cache. Returns the fresh ads.
     */
    suspend fun refreshAds(): Result<List<AdModel>> = getActiveAds(forceRefresh = true)

    private suspend fun fetchFromNetwork(): Result<List<AdModel>> = runCatching {
        SecureLog.i(TAG, "Fetching active ads from API")
        val response = api.getAds(active = true)
        if (!response.success) {
            throw Exception(response.message ?: "Failed to fetch ads")
        }
        response.ads.filter { it.isActive }
            .sortedBy { it.order }
            .take(3)
    }.onFailure { e ->
        SecureLog.e(TAG, "Failed to fetch ads", e)
    }

    private fun loadCachedAds(): List<AdModel> {
        val prefs = prefs() ?: return emptyList()
        val jsonStr = prefs.getString(KEY_ADS_JSON, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<AdModel>>(jsonStr)
        }.getOrDefault(emptyList())
    }

    private fun saveCachedAds(ads: List<AdModel>) {
        val prefs = prefs() ?: return
        runCatching {
            prefs.edit()
                .putString(KEY_ADS_JSON, json.encodeToString(ads))
                .putLong(KEY_ADS_FETCHED_AT, System.currentTimeMillis())
                .apply()
            SecureLog.i(TAG, "Cached ${ads.size} ads")
        }.onFailure { SecureLog.e(TAG, "Failed to cache ads", it) }
    }

    suspend fun getAdById(adId: String): Result<AdModel?> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getAds(active = true)
            response.ads.find { it.id == adId }
        }.onFailure { e ->
            SecureLog.e(TAG, "Failed to fetch ad $adId", e)
        }
    }

    fun shouldShowAds(permissionsReady: Boolean): Boolean {
        return !permissionsReady
    }

    companion object {
        private const val TAG = "AdRepository"
        private const val PREFS_NAME = "ad_cache"
        private const val KEY_ADS_JSON = "cached_ads_json"
        private const val KEY_ADS_FETCHED_AT = "cached_ads_fetched_at"
    }
}
