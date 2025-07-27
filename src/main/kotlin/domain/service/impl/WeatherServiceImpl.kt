package domain.service.impl

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import domain.model.Result
import domain.repository.WeatherRepository
import domain.service.WeatherService

/**
 * Implementation of WeatherService that handles weather business logic.
 */
class WeatherServiceImpl(private val weatherRepository: WeatherRepository) : WeatherService {

    /**
     * Get current weather data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the weather data if successful, or an error if an exception occurred.
     */
    override suspend fun getWeatherData(lat: String, lon: String): Result<Weather> {
        // Validate location parameters
        val validationResult = validateLocationParams(lat, lon)
        if (validationResult.isError) {
            return Result.error(
                validationResult.getOrNull()?.toString() ?: "Invalid location parameters"
            )
        }

        val (validLat, validLon) = validationResult.getOrNull()!!

        // Try to get cached weather data first
        val cachedResult = getCachedWeatherData(validLat, validLon)
        if (cachedResult.isSuccess) {
            return cachedResult
        }

        // If no cached data or cache is expired, get fresh data from repository
        return weatherRepository.getWeatherData(validLat, validLon)
    }

    /**
     * Get air pollution data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the air pollution data if successful, or an error if an exception occurred.
     */
    override suspend fun getAirPollutionData(lat: String, lon: String): Result<AirQuality> {
        // Validate location parameters
        val validationResult = validateLocationParams(lat, lon)
        if (validationResult.isError) {
            return Result.error(
                validationResult.getOrNull()?.toString() ?: "Invalid location parameters"
            )
        }

        val (validLat, validLon) = validationResult.getOrNull()!!

        // Try to get cached air pollution data first
        val cachedResult = getCachedAirPollutionData(validLat, validLon)
        if (cachedResult.isSuccess) {
            return cachedResult
        }

        // If no cached data or cache is expired, get fresh data from repository
        return weatherRepository.getAirPollutionData(validLat, validLon)
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
        // Validate location parameters
        val validationResult = validateLocationParams(lat, lon)
        if (validationResult.isError) {
            return Result.error(
                validationResult.getOrNull()?.toString() ?: "Invalid location parameters"
            )
        }

        val (validLat, validLon) = validationResult.getOrNull()!!

        // Validate limit parameter
        val validLimit = if (limit != null && limit <= 0) {
            null // Use default limit if provided limit is invalid
        } else {
            limit
        }

        return weatherRepository.getHistoricalWeatherData(validLat, validLon, validLimit)
    }

    /**
     * Validate location parameters.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result indicating if the parameters are valid, or an error with validation message.
     */
    override fun validateLocationParams(lat: String?, lon: String?): Result<Pair<String, String>> {
        if (lat.isNullOrBlank() || lon.isNullOrBlank()) {
            return Result.error("Latitude and longitude are required")
        }

        try {
            // Validate latitude (-90 to 90)
            val latValue = lat.toDouble()
            if (latValue < -90 || latValue > 90) {
                return Result.error("Latitude must be between -90 and 90")
            }

            // Validate longitude (-180 to 180)
            val lonValue = lon.toDouble()
            if (lonValue < -180 || lonValue > 180) {
                return Result.error("Longitude must be between -180 and 180")
            }

            return Result.success(Pair(lat, lon))
        } catch (e: NumberFormatException) {
            return Result.error("Latitude and longitude must be valid numbers", e)
        }
    }

    /**
     * Get cached weather data for a location if available.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the cached weather data if available, or an error if not available.
     */
    override suspend fun getCachedWeatherData(lat: String, lon: String): Result<Weather> {
        return try {
            // Get the most recent weather data for the location from the repository
            val historicalResult = weatherRepository.getHistoricalWeatherData(lat, lon, 1)

            if (historicalResult.isSuccess) {
                val weatherList = historicalResult.getOrNull()

                if (weatherList != null && weatherList.isNotEmpty()) {
                    val weather = weatherList.first()

                    // Check if the cached data is recent (less than 30 minutes old)
                    val currentTime = System.currentTimeMillis() / 1000
                    val weatherTime = weather.current?.dt

                    if (weatherTime != null && currentTime - weatherTime < 1800) { // 30 minutes in seconds
                        return Result.success(weather)
                    }
                }
            }

            Result.error("No recent cached weather data available")
        } catch (e: Exception) {
            Result.error("Failed to get cached weather data: ${e.message}", e)
        }
    }

    /**
     * Get cached air pollution data for a location if available.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the cached air pollution data if available, or an error if not available.
     */
    override suspend fun getCachedAirPollutionData(lat: String, lon: String): Result<AirQuality> {
        // For now, we don't cache air pollution data in the database
        // In a real application, we would implement caching for air pollution data as well
        return Result.error("No cached air pollution data available")
    }
}