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
}