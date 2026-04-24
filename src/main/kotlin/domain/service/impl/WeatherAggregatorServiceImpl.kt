package domain.service.impl

import bose.ankush.data.model.ResponseEntitlements
import bose.ankush.data.model.UnifiedWeatherResponse
import domain.model.Result
import domain.model.SubscriptionFeature
import domain.model.SubscriptionFeatureResolver
import domain.repository.UserRepository
import domain.service.WeatherAggregatorService
import domain.service.WeatherService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

class WeatherAggregatorServiceImpl(
    private val weatherService: WeatherService,
    private val userRepository: UserRepository
) : WeatherAggregatorService {

    companion object {
        private const val WEATHER_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val USER_FEATURE_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes
    }

    // Weather response cache keyed on "lat,lon" with TTL
    private data class CachedWeather(val response: UnifiedWeatherResponse, val expiresAt: Long)
    private val weatherCache = ConcurrentHashMap<String, CachedWeather>()

    // User subscription feature cache keyed on email with TTL
    private data class CachedFeatures(val features: Set<SubscriptionFeature>, val expiresAt: Long)
    private val userFeatureCache = ConcurrentHashMap<String, CachedFeatures>()

    override suspend fun getUnifiedWeather(
        lat: String,
        lon: String,
        email: String
    ): Result<UnifiedWeatherResponse> = coroutineScope {
        // Resolve user features from cache or DB
        val features = resolveUserFeatures(email)
        val needsAirQuality = SubscriptionFeature.AIR_QUALITY in features

        // Build a cache key that accounts for entitlement level
        // (different tiers see different data, so cache per tier)
        val cacheKey = "$lat,$lon|${features.hashCode()}"
        val now = System.currentTimeMillis()
        val cached = weatherCache[cacheKey]
        if (cached != null && now < cached.expiresAt) {
            return@coroutineScope Result.success(cached.response)
        }

        // Launch both calls in parallel; air quality only if the user is entitled
        val weatherDeferred = async { weatherService.getWeatherData(lat, lon) }
        val airQualityDeferred = if (needsAirQuality) {
            async { weatherService.getAirPollutionData(lat, lon) }
        } else null

        val weatherResult = weatherDeferred.await()
        val airQualityResult = airQualityDeferred?.await()

        // Weather is mandatory — propagate the error if it fails
        val weather = weatherResult.getOrNull()
            ?: return@coroutineScope Result.error(
                (weatherResult as Result.Error).message
            )

        val unified = UnifiedWeatherResponse(
            current = weather.current,
            hourly = weather.hourly?.takeIf { SubscriptionFeature.HOURLY_FORECAST in features },
            daily = weather.daily?.takeIf { SubscriptionFeature.DAILY_FORECAST in features },
            alerts = weather.alerts?.takeIf { SubscriptionFeature.WEATHER_ALERTS in features },
            airQuality = airQualityResult?.getOrNull(),
            entitlements = ResponseEntitlements(
                hourlyIncluded = SubscriptionFeature.HOURLY_FORECAST in features,
                dailyIncluded = SubscriptionFeature.DAILY_FORECAST in features,
                alertsIncluded = SubscriptionFeature.WEATHER_ALERTS in features,
                airQualityIncluded = needsAirQuality && airQualityResult?.isSuccess == true,
                upgradeRequired = features.size < SubscriptionFeature.values().size
            )
        )

        // Cache the response
        weatherCache[cacheKey] = CachedWeather(unified, now + WEATHER_CACHE_TTL_MS)

        Result.success(unified)
    }

    override fun validateLocationParams(lat: String?, lon: String?): Result<Pair<String, String>> =
        weatherService.validateLocationParams(lat, lon)

    private suspend fun resolveUserFeatures(email: String): Set<SubscriptionFeature> {
        val now = System.currentTimeMillis()
        val cached = userFeatureCache[email]
        if (cached != null && now < cached.expiresAt) {
            return cached.features
        }

        val user = userRepository.findUserByEmail(email).getOrNull()
        val features = if (user != null) SubscriptionFeatureResolver.resolveFeatures(user)
                       else emptySet()

        userFeatureCache[email] = CachedFeatures(features, now + USER_FEATURE_CACHE_TTL_MS)
        return features
    }
}
