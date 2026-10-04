package bose.ankush.route

import bose.ankush.data.model.ApiResponse
import com.androidplay.core.common.Result
import com.androidplay.weatherify.domain.PlaceEvent
import domain.service.PlaceEventService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import util.Constants

@Serializable
data class CreatePlaceEventRequest(
    val placeName: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val title: String,
    val startsAt: String,
    val note: String? = null
)

@Serializable
data class PlaceEventDTO(
    val id: String,
    val placeName: String,
    val lat: Double,
    val lon: Double,
    val title: String,
    val startsAt: String,
    val note: String? = null,
    val createdAt: String
)

private fun PlaceEvent.toDTO() = PlaceEventDTO(
    id = id.toHexString(),
    placeName = placeName,
    lat = lat,
    lon = lon,
    title = title,
    startsAt = startsAt,
    note = note,
    createdAt = createdAt
)

private fun RoutingCall.jwtEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()

fun Route.placeEventRoute(service: PlaceEventService) {
    authenticate("jwt-auth") {
        post("/place-events") {
            val email = call.jwtEmail() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val request = try {
                call.receive<CreatePlaceEventRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse(false, "Invalid request body: ${e.message}", null)
                )
            }

            val input = PlaceEventService.CreateInput(
                placeName = request.placeName,
                lat = request.lat,
                lon = request.lon,
                title = request.title,
                startsAt = request.startsAt,
                note = request.note
            )
            service.validateCreate(input)?.let { message ->
                return@post call.respond(HttpStatusCode.BadRequest, ApiResponse(false, message, null))
            }

            when (val result = service.create(email, input)) {
                is Result.Success -> call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(true, "Place event created", result.data.toDTO())
                )
                is Result.Error -> call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse(false, result.message, null)
                )
            }
        }

        get("/place-events") {
            val email = call.jwtEmail() ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
            // place name is optional when lat/lon are present
            service.validateListCoords(lat, lon)?.let { message ->
                return@get call.respond(HttpStatusCode.BadRequest, ApiResponse(false, message, null))
            }

            when (val result = service.list(email, lat!!, lon!!)) {
                is Result.Success -> call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(true, "Place events retrieved", result.data.map { it.toDTO() })
                )
                is Result.Error -> call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse(false, result.message, null)
                )
            }
        }
    }
}
