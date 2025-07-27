package domain.repository

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import domain.model.Result

/**
 * Repository interface for Weather-related operations.
 * This interface defines the contract for accessing and manipulating weather data.
 */
interface WeatherRepository {
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
     * Save weather data to the database.
     * @param weather The weather data to save.
     * @return Result indicating success or failure.
     */
    suspend fun saveWeatherData(weather: Weather): Result<Boolean>

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
}