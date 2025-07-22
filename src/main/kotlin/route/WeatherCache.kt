package bose.ankush.route

import bose.ankush.config.Environment
import bose.ankush.getSecretValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cache for weather-related resources with 1-hour validity.
 * 
 * This object provides cached access to:
 * - HttpClient for weather API requests
 * - Weather API key
 * - Weather data URL
 * - Air pollution data URL
 * 
 * All cached resources have a 1-hour expiration time.
 */
object WeatherCache {
    // Cache expiration time in milliseconds (1 hour)
    private const val CACHE_EXPIRATION_TIME = 3600000L

    // Cache for HttpClient
    private var cachedWeatherClient: HttpClient? = null
    private var weatherClientExpiration: Long = 0

    // Cache for API key and URLs
    private var cachedApiKey: String? = null
    private var apiKeyExpiration: Long = 0

    private var cachedWeatherUrl: String? = null
    private var weatherUrlExpiration: Long = 0

    private var cachedAirPollutionUrl: String? = null
    private var airPollutionUrlExpiration: Long = 0

    @Synchronized
    fun getWeatherClient(): HttpClient {
        val currentTime = System.currentTimeMillis()

        if (cachedWeatherClient == null || currentTime > weatherClientExpiration) {
            // Dispose previous client if exists
            cachedWeatherClient?.close()

            cachedWeatherClient = HttpClient(CIO) {
                install(HttpCache)
                install(ContentNegotiation) {
                    json(Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                    })
                }
            }
            weatherClientExpiration = currentTime + CACHE_EXPIRATION_TIME
        }

        return cachedWeatherClient!!
    }

    fun getApiKey(): String {
        val currentTime = System.currentTimeMillis()

        if (cachedApiKey == null || currentTime > apiKeyExpiration) {
            cachedApiKey = getSecretValue("weather-data-secret")
            apiKeyExpiration = currentTime + CACHE_EXPIRATION_TIME
        }

        return cachedApiKey!!
    }

    fun getWeatherUrl(): String {
        val currentTime = System.currentTimeMillis()

        if (cachedWeatherUrl == null || currentTime > weatherUrlExpiration) {
            cachedWeatherUrl = Environment.getWeatherUrl()
            weatherUrlExpiration = currentTime + CACHE_EXPIRATION_TIME
        }

        return cachedWeatherUrl!!
    }

    fun getAirPollutionUrl(): String {
        val currentTime = System.currentTimeMillis()

        if (cachedAirPollutionUrl == null || currentTime > airPollutionUrlExpiration) {
            cachedAirPollutionUrl = Environment.getAirPollutionUrl()
            airPollutionUrlExpiration = currentTime + CACHE_EXPIRATION_TIME
        }

        return cachedAirPollutionUrl!!
    }
}
