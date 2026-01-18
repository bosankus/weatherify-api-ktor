package route

import bose.ankush.data.model.ApiResponse
import domain.model.Result
import domain.service.SavedLocationService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import util.Constants

@Serializable
data class SaveLocationRequest(
    val name: String,
    val lat: Double,
    val lon: Double
)

fun Route.savedLocationRoute(service: SavedLocationService) {
    authenticate("jwt-auth") {
        route("/locations") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<SaveLocationRequest>()
                when (val result = service.saveLocation(email, request.name, request.lat, request.lon)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.Created, ApiResponse(true, "Location saved successfully", null))
                    }

                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
                    }
                }
            }

            get {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                when (val result = service.getSavedLocations(email)) {
                    is Result.Success -> {
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(true, "Locations retrieved successfully", result.data)
                        )
                    }

                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
                    }
                }
            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                when (val result = service.deleteSavedLocation(id, email)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, ApiResponse(true, "Location deleted successfully", null))
                    }

                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
                    }
                }
            }
        }
    }
}
