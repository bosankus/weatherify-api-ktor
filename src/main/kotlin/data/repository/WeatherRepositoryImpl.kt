package data.repository

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import data.source.DatabaseModule
import data.source.WeatherApiClient
import domain.model.Result
import domain.repository.WeatherRepository
import kotlinx.coroutines.flow.toList

/**
 * Implementation of WeatherRepository that uses both a database and an API client for data access.
 */
class WeatherRepositoryImpl(
    private val databaseModule: DatabaseModule,
    private val weatherApiClient: WeatherApiClient
) : WeatherRepository {

    /**
     * Get current weather data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the weather data if successful, or an error if an exception occurred.
     */
    override suspend fun getWeatherData(lat: String, lon: String): Result<Weather> {
        // Try to get cached data first
        val cachedResult = getCachedWeatherData(lat, lon)
        if (cachedResult.isSuccess && cachedResult.getOrNull() != null) {
            return cachedResult
        }

        // If no cached data, fetch from API
        val apiResult = weatherApiClient.getWeatherData(lat, lon)

        // If API call is successful, save to database
        if (apiResult.isSuccess) {
            apiResult.getOrNull()?.let { weather ->
                saveWeatherData(weather)
            }
        }

        return apiResult
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

    /**
     * Save weather data to the database.
     * @param weather The weather data to save.
     * @return Result indicating success or failure.
     */
    override suspend fun saveWeatherData(weather: Weather): Result<Boolean> {
        return try {
            databaseModule.getWeatherCollection().insertOne(weather)
            Result.success(true)
        } catch (e: Exception) {
            Result.error("Failed to save weather data: ${e.message}", e)
        }
    }

    /**
     * Get historical weather data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @param limit Maximum number of records to return (optional).
     * @return Result containing a list of historical weather data, or an error if an exception occurred.
     */
    override suspend fun getHistoricalWeatherData(
        lat: String,
        lon: String,
        limit: Int?
    ): Result<List<Weather>> {
        return try {
            val query = databaseModule.createQuery("lat", lat).append("lon", lon)
            val flow = databaseModule.getWeatherCollection().find(query).sort(
                org.bson.Document("dt", -1) // Sort by timestamp descending
            )

            val weatherList = if (limit != null && limit > 0) {
                flow.limit(limit).toList()
            } else {
                flow.toList()
            }

            Result.success(weatherList)
        } catch (e: Exception) {
            Result.error("Failed to get historical weather data: ${e.message}", e)
        }
    }

    /**
     * Get cached weather data for a location if available.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the cached weather data if available, or an error if not available.
     */
    suspend fun getCachedWeatherData(lat: String, lon: String): Result<Weather> {
        return try {
            val query = databaseModule.createQuery("lat", lat).append("lon", lon)
            val weather = databaseModule.getWeatherCollection().find(query)
                .sort(org.bson.Document("dt", -1)) // Sort by timestamp descending
                .limit(1)
                .toList()
                .firstOrNull()

            if (weather != null && weather.current != null) {
                // Check if the cached data is recent (less than 30 minutes old)
                val currentTime = System.currentTimeMillis() / 1000
                val weatherTime = weather.current.dt

                if (weatherTime != null && currentTime - weatherTime < 1800) { // 30 minutes in seconds
                    return Result.success(weather)
                }
            }

            Result.error("No recent cached weather data available")
        } catch (e: Exception) {
            Result.error("Failed to get cached weather data: ${e.message}", e)
        }
    }
}