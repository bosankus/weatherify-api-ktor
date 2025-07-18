package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory.saveWeatherData
import bose.ankush.data.model.Weather
import bose.ankush.getSecretValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

// Configure HttpClient
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

// Initialize API keys and URLs
private val apiKey by lazy { getSecretValue("weather-data-secret") }
private val weatherUrl by lazy { getSecretValue("weather-data-url") }
private val airPollutionUrl by lazy { getSecretValue("air-pollution-url") }

// Extract location parameters from request
private suspend fun ApplicationCall.extractLocationParams(): Pair<String, String>? {
    val lat = request.queryParameters["lat"]
    val lon = request.queryParameters["lon"]

    if (lat.isNullOrBlank() || lon.isNullOrBlank()) {
        respond(mapOf("error" to "Missing mandatory query parameters: lat and lon"))
        return null
    }

    return Pair(lat, lon)
}

// Fetch weather data
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
            additionalParams.forEach { (key, value) ->
                parameter(key, value)
            }
        }.body<T>()
    }
}

fun Route.weatherRoute() {
    route("/get-weather") {
        get {
            val (lat, lon) = call.extractLocationParams() ?: return@get

            fetchWeatherData<Weather>(
                url = weatherUrl,
                lat = lat,
                lon = lon,
                additionalParams = mapOf("exclude" to "minutely")
            ).onSuccess { weatherData ->
                call.respond(weatherData)

                // Save data
                withContext(Dispatchers.IO) {
                    try {
                        saveWeatherData(weatherData)
                    } catch (e: Exception) {
                        println("Error saving weather data: ${e.message}")
                    }
                }
            }.onFailure { e ->
                call.respond(mapOf("error" to e.message))
            }
        }
    }

    route("/get-air-pollution") {
        get {
            val (lat, lon) = call.extractLocationParams() ?: return@get

            fetchWeatherData<String>(
                url = airPollutionUrl,
                lat = lat,
                lon = lon
            ).onSuccess { response ->
                call.respond(response)
            }.onFailure { e ->
                call.respond(mapOf("error" to e.message))
            }
        }
    }
}
