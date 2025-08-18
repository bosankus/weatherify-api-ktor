package bose.ankush.route

import bose.ankush.data.model.UserRole
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import config.JwtConfig
import domain.model.Result
import domain.repository.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Admin User management routes (IAM)
 * - List users with pagination
 * - Update user role
 * - Update user active status
 */
fun Route.userRoute() {
    val userRepository: UserRepository by application.inject()

    // Helper to extract and verify admin JWT
    suspend fun ensureAdminAndGetToken(call: ApplicationCall): Boolean {
        val authHeader = call.request.headers["Authorization"]
        val jwtToken = when {
            authHeader != null && authHeader.startsWith("Bearer ") -> authHeader.substring(7)
            else -> call.request.cookies["jwt_token"]
        }

        if (jwtToken.isNullOrBlank()) {
            call.respondError("Authentication required", Unit, HttpStatusCode.Unauthorized)
            return false
        }
        val decoded = try {
            JwtConfig.verifier.verify(jwtToken)
        } catch (_: Exception) {
            call.respondError("Session expired or invalid token", Unit, HttpStatusCode.Unauthorized)
            return false
        }
        if (!JwtConfig.isAdmin(decoded)) {
            call.respondError("Access denied: Admins only", Unit, HttpStatusCode.Forbidden)
            return false
        }
        return true
    }

    route("/admin") {
        // List users (migrated from AdminAuthRoute)
        get("/users") {
            if (!ensureAdminAndGetToken(call)) return@get

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
                        mapOf(
                            "email" to u.email,
                            "createdAt" to u.createdAt,
                            "role" to (u.role?.name ?: "USER"),
                            "isActive" to u.isActive,
                            "isPremium" to u.isPremium
                        )
                    }

                    val payload = mapOf(
                        "users" to items,
                        "pagination" to mapOf(
                            "page" to page,
                            "pageSize" to pageSize,
                            "totalPages" to totalPages,
                            "totalCount" to totalCount
                        )
                    )

                    call.respondSuccess("Users retrieved", payload)
                }

                is Result.Error -> {
                    call.respondError(result.message, Unit, HttpStatusCode.BadRequest)
                }
            }
        }

        // Update user role
        post("/users/{email}/role") {
            if (!ensureAdminAndGetToken(call)) return@post

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
            if (!ensureAdminAndGetToken(call)) return@post

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
                                call.respondSuccess(
                                    if (req.isActive) "User activated" else "User deactivated",
                                    mapOf("email" to email, "isActive" to req.isActive)
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
    }
}

@Serializable
data class RoleUpdateRequest(val role: String)

@Serializable
data class StatusUpdateRequest(val isActive: Boolean)
