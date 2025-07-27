package data.source

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import bose.ankush.util.getSecretValue
import config.Environment
import domain.model.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import util.Constants

/**
 * Implementation of WeatherApiClient that uses Ktor HttpClient for API calls.
 * This class includes caching for better performance.
 */
class WeatherApiClientImpl : WeatherApiClient {
    // Cache expiration time in milliseconds (10 minutes)
    private val cacheExpirationTime = 600000L

    // HttpClient with caching
    private val httpClient = HttpClient(CIO) {
        install(HttpCache)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    // Cache for API key and URLs
    private var cachedApiKey: String? = null
    private var apiKeyExpiration: Long = 0

    private var cachedWeatherUrl: String? = null
    private var weatherUrlExpiration: Long = 0

    private var cachedAirPollutionUrl: String? = null
    private var airPollutionUrlExpiration: Long = 0

    /**
     * Get current weather data for a location.
     * @param lat Latitude of the location.
     * @param lon Longitude of the location.
     * @return Result containing the weather data if successful, or an error if an exception occurred.
     */
    override suspend fun getWeatherData(lat: String, lon: String): Result<Weather> {
        return try {
            val response = httpClient.get(getWeatherUrl()) {
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
            val response = httpClient.get(getAirPollutionUrl()) {
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
    override fun getApiKey(): String {
        val currentTime = System.currentTimeMillis()

        if (cachedApiKey == null || currentTime > apiKeyExpiration) {
            cachedApiKey = getSecretValue("weather-data-secret")
            apiKeyExpiration = currentTime + cacheExpirationTime
        }

        return cachedApiKey!!
    }

    /**
     * Get the URL for weather API calls.
     * @return The URL.
     */
    override fun getWeatherUrl(): String {
        val currentTime = System.currentTimeMillis()

        if (cachedWeatherUrl == null || currentTime > weatherUrlExpiration) {
            cachedWeatherUrl = Environment.getWeatherUrl()
            weatherUrlExpiration = currentTime + cacheExpirationTime
        }

        return cachedWeatherUrl!!
    }

    /**
     * Get the URL for air pollution API calls.
     * @return The URL.
     */
    override fun getAirPollutionUrl(): String {
        val currentTime = System.currentTimeMillis()

        if (cachedAirPollutionUrl == null || currentTime > airPollutionUrlExpiration) {
            cachedAirPollutionUrl = Environment.getAirPollutionUrl()
            airPollutionUrlExpiration = currentTime + cacheExpirationTime
        }

        return cachedAirPollutionUrl!!
    }

    /**
     * Close the API client and release resources.
     */
    override fun close() {
        httpClient.close()
    }
}