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
    route(Constants.Api.WEATHER_ENDPOINT) {
        // Get weather data
        get {
            val locationParams = call.extractLocationParams() ?: return@get
            val (lat, lon) = locationParams

            fetchWeatherData<Weather>(
                url = WeatherCache.getWeatherUrl(),
                lat = lat,
                lon = lon,
                additionalParams = mapOf(Constants.Api.PARAM_EXCLUDE to Constants.Api.EXCLUDE_MINUTELY)
            ).onSuccess { weatherData ->
                call.respondSuccess(
                    Constants.Messages.WEATHER_RETRIEVED,
                    weatherData,
                    io.ktor.http.HttpStatusCode.OK
                )

                // Save data asynchronously
                withContext(Dispatchers.IO) {
                    val saved = saveWeatherData(weatherData)
                    if (!saved) {
                        println(Constants.Messages.FAILED_SAVE_WEATHER)
                    }
                }
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
            val locationParams = call.extractLocationParams() ?: return@get
            val (lat, lon) = locationParams

            fetchWeatherData<AirQuality>(
                url = WeatherCache.getAirPollutionUrl(),
                lat = lat,
                lon = lon
            ).onSuccess { response ->
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
