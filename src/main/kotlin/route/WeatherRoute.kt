package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory.saveWeatherData
import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        WeatherCache.getWeatherClient().get(url) {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("appid", WeatherCache.getApiKey())
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
                url = WeatherCache.getWeatherUrl(),
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
                url = WeatherCache.getAirPollutionUrl(),
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
