package data.repository

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import data.source.WeatherApiClient
import domain.model.Result
import domain.repository.WeatherRepository

/**
 * Implementation of WeatherRepository that uses an API client for data access.
 * Weather data is no longer stored in MongoDB.
 */
class WeatherRepositoryImpl(
    private val weatherApiClient: WeatherApiClient
) : WeatherRepository {

    /**
     * Get current weather data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the weather data if successful, or an error if an exception occurred.
     */
    override suspend fun getWeatherData(lat: String, lon: String): Result<Weather> {
        // Fetch from API directly (no database storage)
        return weatherApiClient.getWeatherData(lat, lon)
    }

    /**
     * Get air pollution data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the air pollution data if successful, or an error if an exception occurred.
     */
    override suspend fun getAirPollutionData(lat: String, lon: String): Result<AirQuality> {
        return weatherApiClient.getAirPollutionData(lat, lon)
    }
}
