package bose.ankush.route

import bose.ankush.data.model.UserRole
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import bose.ankush.util.WeatherCache
import config.Environment
import domain.model.Result
import domain.repository.UserRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.AuthHelper.getAuthenticatedAdminOrRespond

/**
 * Admin User management routes (IAM)
 * - List users with pagination
 * - Update user role
 * - Update user active status
 */
fun Route.userRoute() {
    val userRepository: UserRepository by application.inject()

    // FCM token registration endpoint (email-based)
    post("/user/{email}/fcm-token") {
        try {
            val email = call.parameters["email"]?.trim()
            if (email.isNullOrEmpty()) {
                call.respondError(
                    "Validation process failed: Missing user email in path",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            val req = try {
                call.receive<FcmTokenRequest>()
            } catch (e: Exception) {
                call.respondError(
                    "Invalid request body: ${e.message}",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            if (req.fcmToken.isBlank()) {
                call.respondError(
                    "Validation process failed: fcmToken is required",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            when (val res = userRepository.updateFcmTokenByEmail(email, req.fcmToken)) {
                is Result.Success -> {
                    if (res.data) {
                        call.respondSuccess("FCM token registered", mapOf("email" to email))
                    } else {
                        call.respondError(
                            "Database operation failed: Failed to update FCM token",
                            Unit,
                            HttpStatusCode.InternalServerError
                        )
                    }
                }

                is Result.Error -> {
                    val message = res.message
                    val status = when {
                        message.contains("not found", ignoreCase = true) -> HttpStatusCode.NotFound
                        message.contains(
                            "validation",
                            ignoreCase = true
                        ) -> HttpStatusCode.BadRequest

                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respondError(message, Unit, status)
                }
            }
        } catch (e: Exception) {
            call.respondError(
                "Internal server error",
                mapOf("error" to (e.message ?: "unknown")),
                HttpStatusCode.InternalServerError
            )
        }
    }



    route("/admin") {
        // List users (migrated from AdminAuthRoute)
        get("/users") {
            call.getAuthenticatedAdminOrRespond() ?: return@get

            // Pagination params
            val pageParam = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val sizeParam = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10
            val page = if (pageParam < 1) 1 else pageParam
            val pageSize = when {
                sizeParam < 1 -> 10
                sizeParam > 100 -> 100
                else -> sizeParam
            }

            when (val result = userRepository.getAllUsers(
                filter = null,
                sortBy = "createdAt",
                sortOrder = -1,
                page = page,
                pageSize = pageSize
            )) {
                is Result.Success -> {
                    val users = result.data.first
                    val totalCount = result.data.second
                    val totalPages =
                        if (totalCount == 0L) 1 else ((totalCount + pageSize - 1) / pageSize).toInt()

                    val items = users.map { u ->
                        UserListItemDTO(
                            email = u.email,
                            createdAt = u.createdAt,
                            role = (u.role?.name ?: "USER"),
                            isActive = u.isActive,
                            isPremium = u.isPremium
                        )
                    }

                    val payload = UsersResponseDTO(
                        users = items,
                        pagination = PaginationDTO(
                            page = page,
                            pageSize = pageSize,
                            totalPages = totalPages,
                            totalCount = totalCount
                        )
                    )

                    call.respondSuccess<UsersResponseDTO>("Users retrieved", payload)
                }

                is Result.Error -> {
                    call.respondError(result.message, Unit, HttpStatusCode.BadRequest)
                }
            }
        }

        // Update user role
        post("/users/{email}/role") {
            call.getAuthenticatedAdminOrRespond() ?: return@post

            val email = call.parameters["email"]?.trim()
            if (email.isNullOrEmpty()) {
                call.respondError(
                    "Email path parameter is required",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            val req = try {
                call.receive<RoleUpdateRequest>()
            } catch (e: Exception) {
                call.respondError(
                    "Invalid request body: ${e.message}",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            val newRole = try {
                UserRole.valueOf(req.role.trim().uppercase())
            } catch (_: Exception) {
                call.respondError("Invalid role: ${req.role}", Unit, HttpStatusCode.BadRequest)
                return@post
            }

            when (val userRes = userRepository.findUserByEmail(email)) {
                is Result.Success -> {
                    val user = userRes.data
                    if (user == null) {
                        call.respondError("User not found", Unit, HttpStatusCode.NotFound)
                        return@post
                    }
                    val updated = user.copy(role = newRole)
                    when (val updRes = userRepository.updateUser(updated)) {
                        is Result.Success -> {
                            if (updRes.data) {
                                call.respondSuccess(
                                    "Role updated",
                                    mapOf("email" to email, "role" to newRole.name)
                                )
                            } else {
                                call.respondError(
                                    "Failed to update user role",
                                    Unit,
                                    HttpStatusCode.InternalServerError
                                )
                            }
                        }

                        is Result.Error -> {
                            call.respondError(
                                updRes.message,
                                Unit,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                }

                is Result.Error -> {
                    call.respondError(userRes.message, Unit, HttpStatusCode.InternalServerError)
                }
            }
        }

        // Update user active status
        post("/users/{email}/status") {
            call.getAuthenticatedAdminOrRespond() ?: return@post

            val email = call.parameters["email"]?.trim()
            if (email.isNullOrEmpty()) {
                call.respondError(
                    "Email path parameter is required",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            val req = try {
                call.receive<StatusUpdateRequest>()
            } catch (e: Exception) {
                call.respondError(
                    "Invalid request body: ${e.message}",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            when (val userRes = userRepository.findUserByEmail(email)) {
                is Result.Success -> {
                    val user = userRes.data
                    if (user == null) {
                        call.respondError("User not found", Unit, HttpStatusCode.NotFound)
                        return@post
                    }
                    val updated = user.copy(isActive = req.isActive)
                    when (val updRes = userRepository.updateUser(updated)) {
                        is Result.Success -> {
                            if (updRes.data) {
                                call.respondSuccess<StatusUpdateResponseDTO>(
                                    if (req.isActive) "User activated" else "User deactivated",
                                    StatusUpdateResponseDTO(
                                        email = email,
                                        isActive = req.isActive
                                    )
                                )
                            } else {
                                call.respondError(
                                    "Failed to update user status",
                                    Unit,
                                    HttpStatusCode.InternalServerError
                                )
                            }
                        }

                        is Result.Error -> {
                            call.respondError(
                                updRes.message,
                                Unit,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                }

                is Result.Error -> {
                    call.respondError(userRes.message, Unit, HttpStatusCode.InternalServerError)
                }
            }
        }

        // Update user premium status
        post("/users/{email}/premium") {
            call.getAuthenticatedAdminOrRespond() ?: return@post

            val email = call.parameters["email"]?.trim()
            if (email.isNullOrEmpty()) {
                call.respondError(
                    "Email path parameter is required",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            val req = try {
                call.receive<PremiumUpdateRequest>()
            } catch (e: Exception) {
                call.respondError(
                    "Invalid request body: ${e.message}",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            when (val userRes = userRepository.findUserByEmail(email)) {
                is Result.Success -> {
                    val user = userRes.data
                    if (user == null) {
                        call.respondError("User not found", Unit, HttpStatusCode.NotFound)
                        return@post
                    }
                    val updated = user.copy(isPremium = req.isPremium)
                    when (val updRes = userRepository.updateUser(updated)) {
                        is Result.Success -> {
                            if (updRes.data) {
                                call.respondSuccess<PremiumUpdateResponseDTO>(
                                    if (req.isPremium) "Premium enabled" else "Premium disabled",
                                    PremiumUpdateResponseDTO(
                                        email = email,
                                        isPremium = req.isPremium
                                    )
                                )
                            } else {
                                call.respondError(
                                    "Failed to update premium status",
                                    Unit,
                                    HttpStatusCode.InternalServerError
                                )
                            }
                        }

                        is Result.Error -> {
                            call.respondError(
                                updRes.message,
                                Unit,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                }

                is Result.Error -> {
                    call.respondError(userRes.message, Unit, HttpStatusCode.InternalServerError)
                }
            }
        }

        // Send promotional notification
        post("/users/{email}/notify") {
            call.getAuthenticatedAdminOrRespond() ?: return@post

            val email = call.parameters["email"]?.trim()
            if (email.isNullOrEmpty()) {
                call.respondError(
                    "Email path parameter is required",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            val req = try {
                call.receive<NotificationRequest>()
            } catch (_: Exception) {
                NotificationRequest()
            }

            when (val findRes = userRepository.findUserByEmail(email)) {
                is Result.Success -> {
                    val user = findRes.data
                    if (user == null) {
                        call.respondError("User not found", Unit, HttpStatusCode.NotFound)
                        return@post
                    }
                    val token = user.fcmToken?.trim()
                    if (token.isNullOrEmpty()) {
                        call.respondError(
                            "No FCM token registered for this user",
                            Unit,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    // If a Cloud Function URL is configured, attempt to call it; otherwise, return success.
                    val fnUrl = Environment.getFcmFunctionUrl()
                    if (fnUrl.isNullOrBlank()) {
                        call.respondSuccess("Notification queued", mapOf("email" to email))
                        return@post
                    }

                    try {
                        val client = WeatherCache.getWeatherClient()
                        val payload = mapOf(
                            "token" to token,
                            "title" to (req.title ?: "Promotion"),
                            "body" to (req.body ?: "Enjoy new features in our app!")
                        )
                        val resp = client.post(fnUrl) {
                            setBody(payload)
                        }
                        val ok = resp.status.value in 200..299
                        if (ok) {
                            call.respondSuccess("Notification sent", mapOf("email" to email))
                        } else {
                            call.respondError(
                                "Failed to send notification via function (status ${'$'}{resp.status.value})",
                                Unit,
                                HttpStatusCode.BadGateway
                            )
                        }
                    } catch (_: Exception) {
                        call.respondError(
                            "Failed to send notification: ${'$'}{e.message}",
                            Unit,
                            HttpStatusCode.BadGateway
                        )
                    }
                }

                is Result.Error -> {
                    call.respondError(findRes.message, Unit, HttpStatusCode.InternalServerError)
                }
            }
        }

        // Clear weather cache
        post("/cache/clear") {
            call.getAuthenticatedAdminOrRespond() ?: return@post
            WeatherCache.clearCache()
            call.respondSuccess("Cache cleared", true)
        }

        // Tools endpoints
        route("/tools") {
            // Health check probing upstream services
            post("/health") {
                call.getAuthenticatedAdminOrRespond() ?: return@post

                val client = WeatherCache.getWeatherClient()
                val weatherUrl = WeatherCache.getWeatherUrl()
                val airUrl = WeatherCache.getAirPollutionUrl()
                val probeWeatherUrl = WeatherCache.getProbeWeatherUrl()
                val probeAirUrl = WeatherCache.getProbeAirPollutionUrl()

                suspend fun probe(url: String): Triple<HttpResponse?, Long, String?> {
                    val start = System.nanoTime()
                    return try {
                        val resp = client.get(url)
                        val end = System.nanoTime()
                        Triple(resp, ((end - start) / 1_000_000), null)
                    } catch (e: Exception) {
                        val end = System.nanoTime()
                        Triple(null, ((end - start) / 1_000_000), e.message)
                    }
                }

                val (wResp, wLatency, wErr) = probe(probeWeatherUrl)
                val (aResp, aLatency, aErr) = probe(probeAirUrl)

                val data = ToolsHealthData(
                    weatherUrl = weatherUrl,
                    probeWeatherUrl = probeWeatherUrl,
                    weatherStatusCode = wResp?.status?.value ?: -1,
                    weatherStatusText = wResp?.status?.description ?: (wErr ?: ""),
                    weatherOk = (wResp?.status?.value ?: 0) in 200..299 && wErr == null,
                    weatherLatencyMs = wLatency,
                    weatherContentType = wResp?.headers?.get("Content-Type"),
                    weatherBytes = try {
                        wResp?.bodyAsText()?.toByteArray(Charsets.UTF_8)?.size
                    } catch (_: Exception) {
                        null
                    },
                    weatherError = wErr,
                    airUrl = airUrl,
                    probeAirUrl = probeAirUrl,
                    airStatusCode = aResp?.status?.value ?: -1,
                    airStatusText = aResp?.status?.description ?: (aErr ?: ""),
                    airOk = (aResp?.status?.value ?: 0) in 200..299 && aErr == null,
                    airLatencyMs = aLatency,
                    airContentType = aResp?.headers?.get("Content-Type"),
                    airBytes = try {
                        aResp?.bodyAsText()?.toByteArray(Charsets.UTF_8)?.size
                    } catch (_: Exception) {
                        null
                    },
                    airError = aErr,
                    timestamp = System.currentTimeMillis()
                )

                call.respondSuccess("Health check complete", data)
            }

            // Warmup critical endpoints for a few locations
            post("/warmup") {
                call.getAuthenticatedAdminOrRespond() ?: return@post

                val client = WeatherCache.getWeatherClient()
                val apiKey = WeatherCache.getApiKey()
                val wBase = WeatherCache.getWeatherUrl()
                val aBase = WeatherCache.getAirPollutionUrl()

                fun urlWithParams(base: String, lat: Double, lon: Double, air: Boolean): String {
                    val sep = if (base.contains("?")) "&" else "?"
                    return if (air) {
                        "$base$sep" + "lat=$lat&lon=$lon&appid=$apiKey"
                    } else {
                        "$base$sep" + "lat=$lat&lon=$lon&exclude=minutely&appid=$apiKey"
                    }
                }

                suspend fun hit(url: String): Triple<Int, Long, String?> {
                    val start = System.nanoTime()
                    return try {
                        val resp = client.get(url)
                        val end = System.nanoTime()
                        Triple(resp.status.value, ((end - start) / 1_000_000), null)
                    } catch (e: Exception) {
                        val end = System.nanoTime()
                        Triple(-1, ((end - start) / 1_000_000), e.message)
                    }
                }

                val locations = listOf(
                    Triple(0.0, 0.0, "Equator"),
                    Triple(37.7749, -122.4194, "San Francisco"),
                    Triple(51.5074, -0.1278, "London")
                )

                val results = mutableListOf<WarmupItem>()
                for ((lat, lon, name) in locations) {
                    val wUrl = urlWithParams(wBase, lat, lon, air = false)
                    val aUrl = urlWithParams(aBase, lat, lon, air = true)

                    val (wCode, wLatency, wErr) = hit(wUrl)
                    val (aCode, aLatency, aErr) = hit(aUrl)

                    results += WarmupItem(
                        lat = lat,
                        lon = lon,
                        name = name,
                        weatherStatusCode = wCode,
                        weatherOk = (wCode in 200..299) && wErr == null,
                        weatherLatencyMs = wLatency,
                        weatherError = wErr,
                        airStatusCode = aCode,
                        airOk = (aCode in 200..299) && aErr == null,
                        airLatencyMs = aLatency,
                        airError = aErr
                    )
                }

                val overallOk = results.all { it.weatherOk && it.airOk }
                val payload = WarmupResponse(
                    results = results,
                    ok = overallOk,
                    timestamp = System.currentTimeMillis()
                )

                call.respondSuccess("Warmup complete", payload)
            }
        }
    }
}

@Serializable
data class RoleUpdateRequest(val role: String)

@Serializable
data class StatusUpdateRequest(val isActive: Boolean)


@Serializable
data class UserListItemDTO(
    val email: String,
    val createdAt: String,
    val role: String,
    val isActive: Boolean,
    val isPremium: Boolean
)

@Serializable
data class PaginationDTO(
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalCount: Long
)

@Serializable
data class UsersResponseDTO(
    val users: List<UserListItemDTO>,
    val pagination: PaginationDTO
)

@Serializable
data class StatusUpdateResponseDTO(
    val email: String,
    val isActive: Boolean
)

@Serializable
data class FcmTokenRequest(
    val fcmToken: String
)

@Serializable
data class PremiumUpdateRequest(val isPremium: Boolean)

@Serializable
data class PremiumUpdateResponseDTO(
    val email: String,
    val isPremium: Boolean
)

@Serializable
data class NotificationRequest(
    val title: String? = null,
    val body: String? = null
)

// Tools DTOs
@Serializable
data class ToolsHealthData(
    val weatherUrl: String,
    val probeWeatherUrl: String,
    val weatherStatusCode: Int,
    val weatherStatusText: String,
    val weatherOk: Boolean,
    val weatherLatencyMs: Long,
    val weatherContentType: String? = null,
    val weatherBytes: Int? = null,
    val weatherError: String? = null,
    val airUrl: String,
    val probeAirUrl: String,
    val airStatusCode: Int,
    val airStatusText: String,
    val airOk: Boolean,
    val airLatencyMs: Long,
    val airContentType: String? = null,
    val airBytes: Int? = null,
    val airError: String? = null,
    val timestamp: Long
)

@Serializable
data class WarmupItem(
    val lat: Double,
    val lon: Double,
    val name: String? = null,
    val weatherStatusCode: Int,
    val weatherOk: Boolean,
    val weatherLatencyMs: Long,
    val weatherError: String? = null,
    val airStatusCode: Int,
    val airOk: Boolean,
    val airLatencyMs: Long,
    val airError: String? = null
)

@Serializable
data class WarmupResponse(
    val results: List<WarmupItem>,
    val ok: Boolean,
    val timestamp: Long
)
