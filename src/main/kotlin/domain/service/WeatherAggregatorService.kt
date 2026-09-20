package domain.service

import bose.ankush.data.model.UnifiedWeatherResponse
import com.androidplay.core.common.Result

interface WeatherAggregatorService {

    suspend fun getUnifiedWeather(
        lat: String,
        lon: String,
        email: String
    ): Result<UnifiedWeatherResponse>

    fun validateLocationParams(lat: String?, lon: String?): Result<Pair<String, String>>

    /**
     * Drops the cached subscription-feature entitlements for [email] so the next request
     * re-reads the user's current plan instead of serving a stale tier for up to
     * USER_FEATURE_CACHE_TTL_MS. Call this whenever a user's premium status changes
     * out-of-band (admin toggle, payment webhook, expiry).
     */
    fun invalidateUserCache(email: String)
}