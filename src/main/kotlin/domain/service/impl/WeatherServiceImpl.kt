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

        // Get fresh data from repository (no database caching)
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

        // Get fresh data from repository (no database caching)
        return weatherRepository.getAirPollutionData(validLat, validLon)
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

}