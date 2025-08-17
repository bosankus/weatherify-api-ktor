package bose.ankush.util

import config.Environment
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cache for weather-related resources with 10-minute expiration.
 * Provides cached access to HttpClient, API key, and URLs.
 */
object WeatherCache {
    // Cache expiration time in milliseconds (10 minutes)
    private const val CACHE_EXPIRATION_TIME = 600000L

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
            // Dispose of the previous client if it exists
            cachedWeatherClient?.close()

            cachedWeatherClient = HttpClient(CIO) {
                install(HttpCache)
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
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