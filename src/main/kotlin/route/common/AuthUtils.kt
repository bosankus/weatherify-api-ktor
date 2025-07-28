package bose.ankush.route.common

import bose.ankush.data.model.User
import bose.ankush.data.model.UserRole
import config.JwtConfig
import domain.model.Result
import domain.repository.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respondRedirect
import org.slf4j.LoggerFactory

/**
 * Utility functions for authentication and authorization
 */
object AuthUtils {
    private val logger = LoggerFactory.getLogger(AuthUtils::class.java)

    /**
     * Verify that the current user has admin role
     * @param call The application call
     * @param userRepository The user repository
     * @param redirectOnFailure Whether to redirect to login page on failure (for UI routes)
     * @param respondOnFailure Whether to respond with error on failure (for API routes)
     * @return The admin user if verification succeeds, null otherwise
     */
    suspend fun verifyAdminAccess(
        call: ApplicationCall,
        userRepository: UserRepository,
        redirectOnFailure: Boolean = false,
        respondOnFailure: Boolean = false
    ): User? {
        // Get JWT principal from call
        val principal = call.principal<JWTPrincipal>()
        if (principal == null) {
            logger.warn("No JWT principal found in request")
            handleAuthFailure(call, "Authentication required", redirectOnFailure, respondOnFailure)
            return null
        }

        // Check if user has admin role directly from JWT
        if (JwtConfig.isAdmin(principal.payload)) {
            // Get user from database to return full user object
            val email = principal.payload.getClaim("email").asString()
            val userResult = userRepository.findUserByEmail(email)

            if (userResult is Result.Success && userResult.data != null) {
                val user = userResult.data
                if (user.role == UserRole.ADMIN && user.isActive) {
                    return user
                }
            }
        }

        // If we get here, user is not an admin or JWT doesn't have role claim
        // Fall back to database check
        val email = principal.payload.getClaim("email").asString()
        if (email.isNullOrEmpty()) {
            logger.warn("No email claim found in JWT")
            handleAuthFailure(
                call,
                "Invalid authentication token",
                redirectOnFailure,
                respondOnFailure
            )
            return null
        }

        // Get user from database
        val userResult = userRepository.findUserByEmail(email)
        if (userResult is Result.Error) {
            logger.error("Failed to verify admin access: ${userResult.message}")
            handleAuthFailure(
                call,
                "Failed to verify admin access: ${userResult.message}",
                redirectOnFailure,
                respondOnFailure
            )
            return null
        }

        val user = (userResult as Result.Success).data
        if (user == null) {
            logger.warn("User not found: $email")
            handleAuthFailure(call, "User not found", redirectOnFailure, respondOnFailure)
            return null
        }

        if (!user.isActive) {
            logger.warn("User account is inactive: $email")
            handleAuthFailure(call, "Account is inactive", redirectOnFailure, respondOnFailure)
            return null
        }

        if (user.role != UserRole.ADMIN) {
            logger.warn("User does not have admin role: $email")
            handleAuthFailure(call, "Admin access required", redirectOnFailure, respondOnFailure)
            return null
        }

        return user
    }

    /**
     * Verify that the current user has moderator or admin role
     * @param call The application call
     * @param userRepository The user repository
     * @param redirectOnFailure Whether to redirect to login page on failure (for UI routes)
     * @param respondOnFailure Whether to respond with error on failure (for API routes)
     * @return The moderator/admin user if verification succeeds, null otherwise
     */
    suspend fun verifyModeratorAccess(
        call: ApplicationCall,
        userRepository: UserRepository,
        redirectOnFailure: Boolean = false,
        respondOnFailure: Boolean = false
    ): User? {
        // Get JWT principal from call
        val principal = call.principal<JWTPrincipal>()
        if (principal == null) {
            logger.warn("No JWT principal found in request")
            handleAuthFailure(call, "Authentication required", redirectOnFailure, respondOnFailure)
            return null
        }

        // Check if user has moderator or admin role directly from JWT
        if (JwtConfig.isModeratorOrAdmin(principal.payload)) {
            // Get user from database to return full user object
            val email = principal.payload.getClaim("email").asString()
            val userResult = userRepository.findUserByEmail(email)

            if (userResult is Result.Success && userResult.data != null) {
                val user = userResult.data
                if ((user.role == UserRole.MODERATOR || user.role == UserRole.ADMIN) && user.isActive) {
                    return user
                }
            }
        }

        // If we get here, user is not a moderator/admin or JWT doesn't have role claim
        // Fall back to database check
        val email = principal.payload.getClaim("email").asString()
        if (email.isNullOrEmpty()) {
            logger.warn("No email claim found in JWT")
            handleAuthFailure(
                call,
                "Invalid authentication token",
                redirectOnFailure,
                respondOnFailure
            )
            return null
        }

        // Get user from database
        val userResult = userRepository.findUserByEmail(email)
        if (userResult is Result.Error) {
            logger.error("Failed to verify moderator access: ${userResult.message}")
            handleAuthFailure(
                call,
                "Failed to verify moderator access: ${userResult.message}",
                redirectOnFailure,
                respondOnFailure
            )
            return null
        }

        val user = (userResult as Result.Success).data
        if (user == null) {
            logger.warn("User not found: $email")
            handleAuthFailure(call, "User not found", redirectOnFailure, respondOnFailure)
            return null
        }

        if (!user.isActive) {
            logger.warn("User account is inactive: $email")
            handleAuthFailure(call, "Account is inactive", redirectOnFailure, respondOnFailure)
            return null
        }

        if (user.role != UserRole.MODERATOR && user.role != UserRole.ADMIN) {
            logger.warn("User does not have moderator or admin role: $email")
            handleAuthFailure(
                call,
                "Moderator access required",
                redirectOnFailure,
                respondOnFailure
            )
            return null
        }

        return user
    }

    /**
     * Handle authentication/authorization failure
     * @param call The application call
     * @param message The error message
     * @param redirect Whether to redirect to login page
     * @param respond Whether to respond with error
     */
    private suspend fun handleAuthFailure(
        call: ApplicationCall,
        message: String,
        redirect: Boolean,
        respond: Boolean
    ) {
        if (redirect) {
            // For UI routes, redirect to login page with error
            if (message == "Admin access required") {
                call.respondRedirect("/admin/login?error=admin_required")
            } else if (message == "Account is inactive") {
                call.respondRedirect("/admin/login?error=account_inactive")
            } else {
                call.respondRedirect("/admin/login?error=session_expired")
            }
        } else if (respond) {
            // For API routes, respond with error
            call.respondError(
                message,
                Unit,
                if (message == "Authentication required" || message == "Invalid authentication token") {
                    HttpStatusCode.Unauthorized
                } else if (message == "Admin access required" || message == "Moderator access required") {
                    HttpStatusCode.Forbidden
                } else {
                    HttpStatusCode.InternalServerError
                }
            )
        }
        // If neither redirect nor respond, just return null and let the caller handle it
    }
}