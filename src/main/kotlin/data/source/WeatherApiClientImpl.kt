package data.source

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import bose.ankush.util.WeatherCache
import domain.model.Result
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import util.Constants

/**
 * Implementation of WeatherApiClient that uses Ktor HttpClient for API calls.
 * Delegates caching and client configuration to WeatherCache to follow SRP/DRY.
 */
class WeatherApiClientImpl : WeatherApiClient {

    /**
     * Get current weather data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the weather data if successful, or an error if an exception occurred.
     */
    override suspend fun getWeatherData(lat: String, lon: String): Result<Weather> {
        return try {
            val response = WeatherCache.getWeatherClient().get(getWeatherUrl()) {
                parameter(Constants.Api.PARAM_LAT, lat)
                parameter(Constants.Api.PARAM_LON, lon)
                parameter(Constants.Api.PARAM_APP_ID, getApiKey())
                parameter(Constants.Api.PARAM_EXCLUDE, Constants.Api.EXCLUDE_MINUTELY)
            }.body<Weather>()
            Result.success(response)
        } catch (e: Exception) {
            Result.error("Failed to get weather data: ${e.message}", e)
        }
    }

    /**
     * Get air pollution data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the air pollution data if successful, or an error if an exception occurred.
     */
    override suspend fun getAirPollutionData(lat: String, lon: String): Result<AirQuality> {
        return try {
            val response = WeatherCache.getWeatherClient().get(getAirPollutionUrl()) {
                parameter(Constants.Api.PARAM_LAT, lat)
                parameter(Constants.Api.PARAM_LON, lon)
                parameter(Constants.Api.PARAM_APP_ID, getApiKey())
            }.body<AirQuality>()
            Result.success(response)
        } catch (e: Exception) {
            Result.error("Failed to get air pollution data: ${e.message}", e)
        }
    }

    /**
     * Get the API key for weather API calls.
     * @return The API key.
     */
    override fun getApiKey(): String = WeatherCache.getApiKey()

    /**
     * Get the URL for weather API calls.
     * @return The URL.
     */
    override fun getWeatherUrl(): String = WeatherCache.getWeatherUrl()

    /**
     * Get the URL for air pollution API calls.
     * @return The URL.
     */
    override fun getAirPollutionUrl(): String = WeatherCache.getAirPollutionUrl()

    /**
     * Close the API client and release resources.
     * No-op because WeatherCache manages the underlying HttpClient lifecycle.
     */
    override fun close() { /* no-op */
    }
}
