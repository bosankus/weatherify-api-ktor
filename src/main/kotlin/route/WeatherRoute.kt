package bose.ankush.route

import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import domain.model.Result
import domain.service.WeatherAggregatorService
import io.ktor.http.*
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
            HttpStatusCode.BadRequest
        )
        return null
    }

    return Pair(lat, lon)
}

/** Unified weather route — returns current, hourly, daily, alerts, and air quality in one call.
 *  Response components are gated by the user's subscription tier. */
fun Route.weatherRoute() {
    val analytics: util.Analytics by application.inject()
    val aggregatorService: WeatherAggregatorService by application.inject()

    route(Constants.Api.WEATHER_ENDPOINT) {
        get {
            val user = call.getAuthenticatedUserOrRespond() ?: return@get

            val (lat, lon) = call.extractLocationParams() ?: return@get

            val validation = aggregatorService.validateLocationParams(lat, lon)
            if (validation.isError) {
                call.respondError(
                    (validation as Result.Error).message,
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@get
            }

            when (val result = aggregatorService.getUnifiedWeather(lat, lon, user.email)) {
                is Result.Success -> {
                    analytics.event(
                        name = "unified_weather_view",
                        params = mapOf("lat" to lat, "lon" to lon),
                        userId = user.email,
                        userAgent = call.request.headers["User-Agent"]
                    )
                    call.respondSuccess(
                        Constants.Messages.WEATHER_RETRIEVED,
                        result.data,
                        HttpStatusCode.OK
                    )
                }
                is Result.Error -> call.respondError(
                    "${Constants.Messages.FAILED_FETCH_WEATHER}: ${result.message}",
                    Unit,
                    HttpStatusCode.InternalServerError
                )
            }
        }
    }
}