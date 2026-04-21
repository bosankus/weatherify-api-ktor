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

class WeatherAggregatorServiceImpl(
    private val weatherService: WeatherService,
    private val userRepository: UserRepository
) : WeatherAggregatorService {

    override suspend fun getUnifiedWeather(
        lat: String,
        lon: String,
        email: String
    ): Result<UnifiedWeatherResponse> = coroutineScope {
        val user = userRepository.findUserByEmail(email).getOrNull()
        val features = if (user != null) SubscriptionFeatureResolver.resolveFeatures(user)
                       else emptySet()
        val needsAirQuality = SubscriptionFeature.AIR_QUALITY in features

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

        Result.success(unified)
    }

    override fun validateLocationParams(lat: String?, lon: String?): Result<Pair<String, String>> =
        weatherService.validateLocationParams(lat, lon)
}