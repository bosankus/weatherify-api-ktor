package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory.saveWeatherData
import bose.ankush.data.model.Weather
import bose.ankush.getSecretValue
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import data.model.AirQuality
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Configure HttpClient with proper caching and serialization
 */
private val weatherClient = HttpClient(CIO) {
    install(HttpCache)
    install(ContentNegotiation) {
        json(Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        })
    }
}

// Initialize API keys and URLs through lazy initialization
private val apiKey by lazy { getSecretValue("weather-data-secret") }
private val weatherUrl by lazy { getSecretValue("weather-data-url") }
private val airPollutionUrl by lazy { getSecretValue("air-pollution-url") }

/**
 * Extract location parameters from the request
 * @return Pair of latitude and longitude or null if missing
 */
private suspend fun ApplicationCall.extractLocationParams(): Pair<String, String>? {
    val lat = request.queryParameters["lat"]
    val lon = request.queryParameters["lon"]

    if (lat.isNullOrBlank() || lon.isNullOrBlank()) {
        respondError("Missing mandatory query parameters: lat and lon", Unit)
        return null
    }

    return Pair(lat, lon)
}

/**
 * Generic function to fetch weather data from APIs
 * @param url API endpoint URL
 * @param lat Latitude
 * @param lon Longitude
 * @param additionalParams Additional query parameters
 * @return Result containing the response or an exception
 */
private suspend inline fun <reified T> fetchWeatherData(
    url: String,
    lat: String,
    lon: String,
    additionalParams: Map<String, String> = emptyMap()
): Result<T> {
    return runCatching {
        weatherClient.get(url) {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("appid", apiKey)
            additionalParams.forEach { (key, value) -> parameter(key, value) }
        }.body<T>()
    }
}

/**
 * Weather API routes
 */
fun Route.weatherRoute() {
    route("/weather") {
        // Get weather data
        get {
            val locationParams = call.extractLocationParams() ?: return@get
            val (lat, lon) = locationParams

            fetchWeatherData<Weather>(
                url = weatherUrl,
                lat = lat,
                lon = lon,
                additionalParams = mapOf("exclude" to "minutely")
            ).onSuccess { weatherData ->
                call.respondSuccess("Weather data retrieved successfully", weatherData)

                // Save data asynchronously
                withContext(Dispatchers.IO) {
                    val saved = saveWeatherData(weatherData)
                    if (!saved) {
                        println("Failed to save weather data")
                    }
                }
            }.onFailure { e ->
                call.respondError("Failed to fetch weather data: ${e.message}", Unit)
            }
        }
    }

    route("/air-pollution") {
        get {
            val locationParams = call.extractLocationParams() ?: return@get
            val (lat, lon) = locationParams

            fetchWeatherData<AirQuality>(
                url = airPollutionUrl,
                lat = lat,
                lon = lon
            ).onSuccess { response ->
                call.respondSuccess("Air pollution data retrieved successfully", response)
            }.onFailure { e ->
                call.respondError("Failed to fetch air pollution data: ${e.message}", Unit)
            }
        }
    }
}
