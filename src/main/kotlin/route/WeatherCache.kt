package bose.ankush.route

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import bose.ankush.getSecretValue

/**
 * Cache for weather-related resources with 1-hour validity
 */
object WeatherCache {
    // Cache expiration time in milliseconds (2 hour)
    private const val CACHE_EXPIRATION_TIME = 7200000L
    
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
    
    /**
     * Get the HttpClient for weather API requests
     * Creates a new client if none exists or if the cached one has expired
     */
    fun getWeatherClient(): HttpClient {
        val currentTime = System.currentTimeMillis()
        
        if (cachedWeatherClient == null || currentTime > weatherClientExpiration) {
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
    
    /**
     * Get the API key for weather services
     * Fetches a new key if none exists or if the cached one has expired
     */
    fun getApiKey(): String {
        val currentTime = System.currentTimeMillis()
        
        if (cachedApiKey == null || currentTime > apiKeyExpiration) {
            cachedApiKey = getSecretValue("weather-data-secret")
            apiKeyExpiration = currentTime + CACHE_EXPIRATION_TIME
        }
        
        return cachedApiKey!!
    }
    
    /**
     * Get the weather data URL
     * Fetches a new URL if none exists or if the cached one has expired
     */
    fun getWeatherUrl(): String {
        val currentTime = System.currentTimeMillis()
        
        if (cachedWeatherUrl == null || currentTime > weatherUrlExpiration) {
            cachedWeatherUrl = getSecretValue("weather-data-url")
            weatherUrlExpiration = currentTime + CACHE_EXPIRATION_TIME
        }
        
        return cachedWeatherUrl!!
    }
    
    /**
     * Get the air pollution data URL
     * Fetches a new URL if none exists or if the cached one has expired
     */
    fun getAirPollutionUrl(): String {
        val currentTime = System.currentTimeMillis()
        
        if (cachedAirPollutionUrl == null || currentTime > airPollutionUrlExpiration) {
            cachedAirPollutionUrl = getSecretValue("air-pollution-url")
            airPollutionUrlExpiration = currentTime + CACHE_EXPIRATION_TIME
        }
        
        return cachedAirPollutionUrl!!
    }
}