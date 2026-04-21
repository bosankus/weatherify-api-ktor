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
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val lat: Double,
    val lon: Double
)

private fun RoutingCall.jwtEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()

private suspend fun RoutingCall.respondPremiumRequired() =
    respond(HttpStatusCode.Forbidden, ApiResponse(false, "Active premium subscription required", null))

fun Route.savedLocationRoute(service: SavedLocationService) {

    authenticate("jwt-auth") {

        get("/search-place") {
            val email = call.jwtEmail() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (!service.isPremiumActive(email)) return@get call.respondPremiumRequired()

            val query = call.request.queryParameters["q"]
            if (query.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse(false, "Missing required query parameter: q", null)
                )
            }

            when (val result = service.searchPlace(query)) {
                is Result.Success -> call.respond(HttpStatusCode.OK, ApiResponse(true, "Places found", result.data))
                is Result.Error -> call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
            }
        }

        post("/save-location") {
            val email = call.jwtEmail() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            if (!service.isPremiumActive(email)) return@post call.respondPremiumRequired()

            val request = call.receive<SaveLocationRequest>()
            when (val result = service.saveLocation(
                email, request.name, request.city, request.state, request.country, request.lat, request.lon
            )) {
                is Result.Success -> call.respond(HttpStatusCode.Created, ApiResponse(true, "Location saved successfully", null))
                is Result.Error -> call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
            }
        }

        get("/saved-places") {
            val email = call.jwtEmail() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (!service.isPremiumActive(email)) return@get call.respondPremiumRequired()

            when (val result = service.getSavedLocations(email)) {
                is Result.Success -> call.respond(HttpStatusCode.OK, ApiResponse(true, "Saved places retrieved successfully", result.data))
                is Result.Error -> call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
            }
        }

        delete("/saved-places/{id}") {
            val email = call.jwtEmail() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            if (!service.isPremiumActive(email)) return@delete call.respondPremiumRequired()

            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse(false, "Missing location id", null))

            when (val result = service.deleteSavedLocation(id, email)) {
                is Result.Success -> call.respond(HttpStatusCode.OK, ApiResponse(true, "Location deleted successfully", null))
                is Result.Error -> call.respond(HttpStatusCode.InternalServerError, ApiResponse(false, result.message, null))
            }
        }
    }
}
