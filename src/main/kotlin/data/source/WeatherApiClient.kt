package data.source

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import domain.model.Result

/**
 * Interface for weather API client.
 * This interface defines the contract for making weather-related API calls.
 */
interface WeatherApiClient {
    /**
     * Get current weather data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the weather data if successful, or an error if an exception occurred.
     */
    suspend fun getWeatherData(lat: String, lon: String): Result<Weather>

    /**
     * Get air pollution data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the air pollution data if successful, or an error if an exception occurred.
     */
    suspend fun getAirPollutionData(lat: String, lon: String): Result<AirQuality>

    /**
     * Get the API key for weather API calls.
     * @return The API key.
     */
    fun getApiKey(): String

    /**
     * Get the URL for weather API calls.
     * @return The URL.
     */
    fun getWeatherUrl(): String

    /**
     * Get the URL for air pollution API calls.
     * @return The URL.
     */
    fun getAirPollutionUrl(): String

    /**
     * Close the API client and release resources.
     */
    fun close()
}