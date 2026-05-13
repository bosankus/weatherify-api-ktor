package bose.ankush.util

import com.androidplay.core.secrets.getSecretValue
import config.Environment
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Caches weather HttpClient, API key, and URLs with a 10-minute TTL. */
object WeatherCache {
    private const val CACHE_EXPIRATION_TIME = 600000L

    private val mutex = Mutex()

    private var cachedWeatherClient: HttpClient? = null
    private var weatherClientExpiration: Long = 0

    private var cachedApiKey: String? = null
    private var apiKeyExpiration: Long = 0

    private var cachedWeatherUrl: String? = null
    private var weatherUrlExpiration: Long = 0

    private var cachedAirPollutionUrl: String? = null
    private var airPollutionUrlExpiration: Long = 0

    suspend fun getWeatherClient(): HttpClient = mutex.withLock {
        val currentTime = System.currentTimeMillis()

        if (cachedWeatherClient == null || currentTime > weatherClientExpiration) {
            cachedWeatherClient?.close()

            cachedWeatherClient = HttpClient(CIO) {
                install(HttpCache)
                install(ContentNegotiation) {
                    json(Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 5000
                    connectTimeoutMillis = 3000
                    socketTimeoutMillis = 5000
                }
            }
            weatherClientExpiration = currentTime + CACHE_EXPIRATION_TIME
        }

        cachedWeatherClient!!
    }

    suspend fun getApiKey(): String = mutex.withLock {
        val currentTime = System.currentTimeMillis()

        if (cachedApiKey == null || currentTime > apiKeyExpiration) {
            cachedApiKey = getSecretValue("weather-data-secret")
            apiKeyExpiration = currentTime + CACHE_EXPIRATION_TIME
        }

        cachedApiKey!!
    }

    suspend fun getWeatherUrl(): String = mutex.withLock {
        val currentTime = System.currentTimeMillis()

        if (cachedWeatherUrl == null || currentTime > weatherUrlExpiration) {
            cachedWeatherUrl = Environment.getWeatherUrl()
            weatherUrlExpiration = currentTime + CACHE_EXPIRATION_TIME
        }

        cachedWeatherUrl!!
    }

    suspend fun getAirPollutionUrl(): String = mutex.withLock {
        val currentTime = System.currentTimeMillis()

        if (cachedAirPollutionUrl == null || currentTime > airPollutionUrlExpiration) {
            cachedAirPollutionUrl = Environment.getAirPollutionUrl()
            airPollutionUrlExpiration = currentTime + CACHE_EXPIRATION_TIME
        }

        cachedAirPollutionUrl!!
    }

    suspend fun clearCache() = mutex.withLock {
        try {
            cachedWeatherClient?.close()
        } catch (_: Exception) {
        }
        cachedWeatherClient = null
        weatherClientExpiration = 0

        cachedApiKey = null
        apiKeyExpiration = 0

        cachedWeatherUrl = null
        weatherUrlExpiration = 0

        cachedAirPollutionUrl = null
        airPollutionUrlExpiration = 0
    }

    /** Probe URL for OWM OneCall; uses exclude=minutely to minimize payload. */
    suspend fun getProbeWeatherUrl(): String {
        val base = getWeatherUrl()
        val apiKey = getApiKey()
        val sep = if (base.contains("?")) "&" else "?"
        return "${base}${sep}lat=0&lon=0&exclude=minutely&appid=${apiKey}"
    }

    suspend fun getProbeAirPollutionUrl(): String {
        val base = getAirPollutionUrl()
        val apiKey = getApiKey()
        val sep = if (base.contains("?")) "&" else "?"
        return "${base}${sep}lat=0&lon=0&appid=${apiKey}"
    }
}
