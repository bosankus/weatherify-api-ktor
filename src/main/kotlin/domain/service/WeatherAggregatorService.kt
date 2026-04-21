package domain.service

import bose.ankush.data.model.UnifiedWeatherResponse
import domain.model.Result

interface WeatherAggregatorService {

    suspend fun getUnifiedWeather(
        lat: String,
        lon: String,
        email: String
    ): Result<UnifiedWeatherResponse>

    fun validateLocationParams(lat: String?, lon: String?): Result<Pair<String, String>>
}