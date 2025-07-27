package domain.service

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import domain.model.Result

/**
 * Service interface for weather-related operations.
 * This interface defines the contract for weather business logic.
 */
interface WeatherService {
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
     * Get historical weather data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @param limit Maximum number of records to return (optional).
     * @return Result containing a list of historical weather data, or an error if an exception occurred.
     */
    suspend fun getHistoricalWeatherData(
        lat: String,
        lon: String,
        limit: Int? = null
    ): Result<List<Weather>>

    /**
     * Validate location parameters.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result indicating if the parameters are valid, or an error with validation message.
     */
    fun validateLocationParams(lat: String?, lon: String?): Result<Pair<String, String>>

    /**
     * Get cached weather data for a location if available.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the cached weather data if available, or an error if not available.
     */
    suspend fun getCachedWeatherData(lat: String, lon: String): Result<Weather>

    /**
     * Get cached air pollution data for a location if available.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the cached air pollution data if available, or an error if not available.
     */
    suspend fun getCachedAirPollutionData(lat: String, lon: String): Result<AirQuality>
}