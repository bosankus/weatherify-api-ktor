package bose.ankush.route

import bose.ankush.data.model.ApiResponse
import com.androidplay.core.common.Result
import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.weatherify.domain.PlaceEventSync
import domain.service.PlaceEventService
import domain.service.PlaceEventService.CreateOutcome
import domain.service.PlaceEventService.DeleteOutcome
import domain.service.PlaceEventService.UpdateOutcome
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import util.Constants
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class CreatePlaceEventRequest(
    /** Optional client-generated UUID; makes the create idempotent. */
    val id: String? = null,
    val placeName: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val title: String,
    val startsAt: String,
    val note: String? = null
)

@Serializable
data class UpdatePlaceEventRequest(
    val placeName: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val title: String,
    val startsAt: String,
    val note: String? = null,
    /** Optional conflict base (ISO-8601 instant); wins over an If-Unmodified-Since header. */
    val baseUpdatedAt: String? = null
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
    val createdAt: String,
    /** Last server change; createdAt for rows written before sync existed. */
    val updatedAt: String,
    /** Set when soft-deleted (tombstone); always present in JSON, null when live. */
    val deletedAt: String?
)

@Serializable
data class PlaceEventSyncDTO(
    val items: List<PlaceEventDTO>,
    /** Pass as `since` to fetch the next page; null when this page reached the end. */
    val nextSince: String?,
    /** Cursor after the last item (or the request cursor when empty); store it for the next sync. */
    val checkpoint: String?,
    val hasMore: Boolean
)

internal fun PlaceEvent.toDTO() = PlaceEventDTO(
    id = publicId,
    placeName = placeName,
    lat = lat,
    lon = lon,
    title = title,
    startsAt = startsAt,
    note = note,
    createdAt = createdAt,
    updatedAt = PlaceEventSync.format(PlaceEventSync.effectiveUpdatedAt(this)),
    deletedAt = deletedAt
)

private fun RoutingCall.jwtEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()

private const val NOT_FOUND_MESSAGE = "Place event not found"

private suspend fun RoutingCall.respondNotFound() =
    respond(HttpStatusCode.NotFound, ApiResponse(false, NOT_FOUND_MESSAGE, null))

private suspend fun RoutingCall.respondBadRequest(message: String) =
    respond(HttpStatusCode.BadRequest, ApiResponse(false, message, null))

/**
 * Conflict base from the body (ISO instant) or the If-Unmodified-Since header (ISO instant or
 * HTTP-date). Returns Result.Error with a 400 message when malformed; Success(null) when absent.
 */
private fun RoutingCall.updateBase(bodyBase: String?): Result<PlaceEventService.UpdateBase?> {
    if (!bodyBase.isNullOrBlank()) {
        return runCatching { Instant.parse(bodyBase.trim()) }.getOrNull()
            ?.let { Result.success(PlaceEventService.UpdateBase(it)) }
            ?: Result.error("baseUpdatedAt must be a valid ISO-8601 instant")
    }
    val header = request.headers[HttpHeaders.IfUnmodifiedSince]?.trim()
    if (header.isNullOrEmpty()) return Result.success(null)
    runCatching { Instant.parse(header) }.getOrNull()?.let {
        return Result.success(PlaceEventService.UpdateBase(it))
    }
    runCatching { ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()?.let {
        return Result.success(PlaceEventService.UpdateBase(it, secondPrecision = true))
    }
    return Result.error("If-Unmodified-Since must be an ISO-8601 instant or an HTTP-date")
}

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

            val clientId = request.id?.let { raw ->
                service.normalizeClientId(raw)
                    ?: return@post call.respondBadRequest("id must be a UUID string")
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
                return@post call.respondBadRequest(message)
            }

            when (val result = service.createWithClientId(email, clientId, input)) {
                is Result.Success -> when (val outcome = result.data) {
                    is CreateOutcome.Created -> call.respond(
                        HttpStatusCode.Created,
                        ApiResponse(true, "Place event created", outcome.event.toDTO())
                    )
                    is CreateOutcome.Existing -> call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(true, "Place event already exists", outcome.event.toDTO())
                    )
                    CreateOutcome.NotFound -> call.respondNotFound()
                }
                is Result.Error -> call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse(false, result.message, null)
                )
            }
        }

        // Registered before the {id} routes; a literal segment wins over a parameter anyway.
        get("/place-events/sync") {
            val email = call.jwtEmail() ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val sinceRaw = call.request.queryParameters["since"]
            val cursor = if (sinceRaw.isNullOrBlank()) {
                null
            } else {
                PlaceEventSync.parseCursor(sinceRaw)
                    ?: return@get call.respondBadRequest(
                        "since must be an ISO-8601 instant or a nextSince/checkpoint cursor"
                    )
            }

            when (val result = service.sync(email, cursor)) {
                is Result.Success -> call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        true,
                        "Place event changes retrieved",
                        PlaceEventSyncDTO(
                            items = result.data.items.map { it.toDTO() },
                            nextSince = result.data.nextSince,
                            checkpoint = result.data.checkpoint,
                            hasMore = result.data.nextSince != null
                        )
                    )
                )
                is Result.Error -> call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse(false, result.message, null)
                )
            }
        }

        put("/place-events/{id}") {
            val email = call.jwtEmail() ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val id = call.parameters["id"].orEmpty()

            val request = try {
                call.receive<UpdatePlaceEventRequest>()
            } catch (e: Exception) {
                return@put call.respond(
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
                return@put call.respondBadRequest(message)
            }
            val base = when (val parsed = call.updateBase(request.baseUpdatedAt)) {
                is Result.Success -> parsed.data
                is Result.Error -> return@put call.respondBadRequest(parsed.message)
            }

            when (val result = service.update(email, id, input, base)) {
                is Result.Success -> when (val outcome = result.data) {
                    is UpdateOutcome.Updated -> call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(true, "Place event updated", outcome.event.toDTO())
                    )
                    is UpdateOutcome.Conflict -> call.respond(
                        HttpStatusCode.Conflict,
                        ApiResponse(false, "Place event was changed on the server", outcome.current.toDTO())
                    )
                    UpdateOutcome.NotFound -> call.respondNotFound()
                }
                is Result.Error -> call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse(false, result.message, null)
                )
            }
        }

        delete("/place-events/{id}") {
            val email = call.jwtEmail() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            val id = call.parameters["id"].orEmpty()

            when (val result = service.delete(email, id)) {
                is Result.Success -> when (result.data) {
                    DeleteOutcome.Deleted -> call.respond(HttpStatusCode.NoContent)
                    DeleteOutcome.NotFound -> call.respondNotFound()
                }
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
