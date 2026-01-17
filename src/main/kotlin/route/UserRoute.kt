package bose.ankush.route

import bose.ankush.data.model.UserRole
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import bose.ankush.util.PasswordUtil
import bose.ankush.util.WeatherCache
import domain.model.Result
import domain.repository.UserRepository
import domain.service.NotificationService
import domain.service.UnregisteredFcmTokenException
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
    val notificationService: NotificationService by application.inject()

    // Wrap all admin routes under /admin prefix
    route("/admin") {
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

        route("/users") {
            // List users (migrated from AdminAuthRoute)
            get {
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
            post("/{email}/role") {
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
            post("/{email}/status") {
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
            post("/{email}/premium") {
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
            post("/{email}/notify") {
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

                        // Send notification using Firebase Admin SDK
                        val title = req.title?.takeIf { it.isNotBlank() } ?: "Promotion"
                        val body = req.body?.takeIf { it.isNotBlank() } ?: "Enjoy new features in our app!"

                        val result = notificationService.sendNotification(token, title, body)

                        result.fold(
                            onSuccess = { messageId ->
                                call.respondSuccess(
                                    "Notification sent successfully",
                                    mapOf(
                                        "email" to email,
                                        "messageId" to messageId
                                    )
                                )
                            },
                            onFailure = { error ->
                                if (error is UnregisteredFcmTokenException) {
                                    userRepository.clearFcmTokenByEmail(email)
                                    call.respondError(
                                        "FCM token is unregistered and has been cleared",
                                        Unit,
                                        HttpStatusCode.Gone
                                    )
                                } else {
                                    call.respondError(
                                        "Failed to send notification: ${error.message}",
                                        Unit,
                                        HttpStatusCode.InternalServerError
                                    )
                                }
                            }
                        )
                    }

                    is Result.Error -> {
                        call.respondError(findRes.message, Unit, HttpStatusCode.InternalServerError)
                    }
                }
            }

            // Reset user password
            post("/{email}/reset-password") {
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
                    call.receive<ResetPasswordRequest>()
                } catch (e: Exception) {
                    call.respondError(
                        "Invalid request body: ${e.message}",
                        Unit,
                        HttpStatusCode.BadRequest
                    )
                    return@post
                }

                val newPassword = req.newPassword.trim()
                if (newPassword.isEmpty()) {
                    call.respondError("New password is required", Unit, HttpStatusCode.BadRequest)
                    return@post
                }

                if (!PasswordUtil.validatePasswordStrength(newPassword)) {
                    call.respondError(
                        "Password must be at least 8 characters with upper, lower, number, and special.",
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
                        val updated = user.copy(passwordHash = PasswordUtil.hashPassword(newPassword))
                        when (val updRes = userRepository.updateUser(updated)) {
                            is Result.Success -> {
                                if (updRes.data) {
                                    call.respondSuccess(
                                        "Password reset successfully",
                                        mapOf("email" to email)
                                    )
                                } else {
                                    call.respondError(
                                        "Failed to reset password",
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

            // Delete user
            delete("/{email}") {
                call.getAuthenticatedAdminOrRespond() ?: return@delete

                val email = call.parameters["email"]?.trim()
                if (email.isNullOrEmpty()) {
                    call.respondError(
                        "Email path parameter is required",
                        Unit,
                        HttpStatusCode.BadRequest
                    )
                    return@delete
                }

                when (val result = userRepository.deleteUserByEmail(email)) {
                    is Result.Success -> {
                        if (result.data) {
                            call.respondSuccess("User deleted", mapOf("email" to email))
                        } else {
                            call.respondError("Failed to delete user", Unit, HttpStatusCode.InternalServerError)
                        }
                    }

                    is Result.Error -> {
                        val status = if (result.message.contains("not found", ignoreCase = true)) {
                            HttpStatusCode.NotFound
                        } else {
                            HttpStatusCode.InternalServerError
                        }
                        call.respondError(result.message, Unit, status)
                    }
                }
            }
        }

        // Clear weather cache
        route("/cache") {
            post("/clear") {
                call.getAuthenticatedAdminOrRespond() ?: return@post
                WeatherCache.clearCache()
                call.respondSuccess("Cache cleared", true)
            }
        }

        // Tools endpoints removed - AdminAuthRoute.kt already has /tools
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
data class ResetPasswordRequest(
    val newPassword: String
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
@Suppress("unused")
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

@Suppress("unused")
@Serializable
data class WarmupResponse(
    val results: List<WarmupItem>,
    val ok: Boolean,
    val timestamp: Long
)
