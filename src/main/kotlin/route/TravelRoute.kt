package bose.ankush.route

import bose.ankush.data.model.ApiResponse
import domain.model.Result
import domain.service.TripService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import util.Constants

@Serializable
data class CreateTripRequest(
    val destinationName: String,
    val lat: Double,
    val lon: Double,
    val startDate: Long,
    val endDate: Long
)

fun Route.travelRoute(service: TripService) {
    authenticate("jwt-auth") {
        route("/travel") {
            // Get trips for the user
            get("/trips") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                when (val result = service.getTrips(email)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, ApiResponse(true, "Trips retrieved", result.data))
                    }

                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
                    }
                }
            }

            // Create a new trip
            post("/trips") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val req = call.receive<CreateTripRequest>()
                when (val result =
                    service.saveTrip(email, req.destinationName, req.lat, req.lon, req.startDate, req.endDate)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.Created, ApiResponse(true, "Trip created", null))
                    }

                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
                    }
                }
            }

            // Get active trip with AI intelligence
            get("/active-intelligence") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                when (val result = service.getActiveTripIntelligence(email)) {
                    is Result.Success -> {
                        if (result.data == null) {
                            call.respond(HttpStatusCode.NotFound, ApiResponse(false, "No active trip found", null))
                        } else {
                            val payload = mapOf(
                                "trip" to result.data.first,
                                "intelligence" to result.data.second
                            )
                            call.respond(HttpStatusCode.OK, ApiResponse(true, "Intelligence retrieved", payload))
                        }
                    }

                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
                    }
                }
            }

            // Delete a trip
            delete("/trips/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                when (val result = service.deleteTrip(id, email)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, ApiResponse(true, "Trip deleted", null))
                    }

                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
                    }
                }
            }
        }
    }
}
