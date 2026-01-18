package bose.ankush.route

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import bose.ankush.util.WeatherCache
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import util.AuthHelper.getAuthenticatedUserOrRespond
import util.Constants

/** Extract latitude and longitude from request */
private suspend fun ApplicationCall.extractLocationParams(): Pair<String, String>? {
    val lat = request.queryParameters[Constants.Api.PARAM_LAT]
    val lon = request.queryParameters[Constants.Api.PARAM_LON]

    if (lat.isNullOrBlank() || lon.isNullOrBlank()) {
        respondError(
            Constants.Messages.MISSING_LOCATION_PARAMS,
            Unit,
            io.ktor.http.HttpStatusCode.BadRequest
        )
        return null
    }

    return Pair(lat, lon)
}

/** Generic function to fetch weather data from APIs */
private suspend inline fun <reified T> fetchWeatherData(
    url: String,
    lat: String,
    lon: String,
    additionalParams: Map<String, String> = emptyMap()
): Result<T> {
    return runCatching {
        WeatherCache.getWeatherClient().get(url) {
            parameter(Constants.Api.PARAM_LAT, lat)
            parameter(Constants.Api.PARAM_LON, lon)
            parameter(Constants.Api.PARAM_APP_ID, WeatherCache.getApiKey())
            additionalParams.forEach { (key, value) -> parameter(key, value) }
        }.body<T>()
    }
}

/** Weather API routes */
fun Route.weatherRoute() {
    val analytics: util.Analytics by application.inject()

    route(Constants.Api.WEATHER_ENDPOINT) {
        // Get weather data
        get {
            // Authenticate user using unified helper
            val user = call.getAuthenticatedUserOrRespond() ?: return@get
            println("User with email ${user.email} is accessing weather data")

            val locationParams = call.extractLocationParams() ?: return@get
            val (lat, lon) = locationParams

            fetchWeatherData<Weather>(
                url = WeatherCache.getWeatherUrl(),
                lat = lat,
                lon = lon,
                additionalParams = mapOf(Constants.Api.PARAM_EXCLUDE to Constants.Api.EXCLUDE_MINUTELY)
            ).onSuccess { weatherData ->
                // Analytics: weather_view
                analytics.event(
                    name = "weather_view",
                    params = mapOf(
                        "lat" to lat,
                        "lon" to lon
                    ),
                    userId = user.email,
                    userAgent = call.request.headers["User-Agent"]
                )

                call.respondSuccess(
                    Constants.Messages.WEATHER_RETRIEVED,
                    weatherData,
                    io.ktor.http.HttpStatusCode.OK
                )
            }.onFailure { e ->
                call.respondError(
                    "${Constants.Messages.FAILED_FETCH_WEATHER}: ${e.message}",
                    Unit,
                    io.ktor.http.HttpStatusCode.InternalServerError
                )
            }
        }
    }

    route(Constants.Api.AIR_POLLUTION_ENDPOINT) {
        get {
            // Authenticate user using unified helper
            val user = call.getAuthenticatedUserOrRespond() ?: return@get
            println("User with email ${user.email} is accessing air pollution data")

            val locationParams = call.extractLocationParams() ?: return@get
            val (lat, lon) = locationParams

            fetchWeatherData<AirQuality>(
                url = WeatherCache.getAirPollutionUrl(),
                lat = lat,
                lon = lon
            ).onSuccess { response ->
                // Analytics: air_quality_view
                analytics.event(
                    name = "air_quality_view",
                    params = mapOf(
                        "lat" to lat,
                        "lon" to lon
                    ),
                    userId = user.email,
                    userAgent = call.request.headers["User-Agent"]
                )

                call.respondSuccess(
                    Constants.Messages.AIR_POLLUTION_RETRIEVED,
                    response,
                    io.ktor.http.HttpStatusCode.OK
                )
            }.onFailure { e ->
                call.respondError(
                    "${Constants.Messages.FAILED_FETCH_AIR_POLLUTION}: ${e.message}",
                    Unit,
                    io.ktor.http.HttpStatusCode.InternalServerError
                )
            }
        }
    }
}
