package util

import bose.ankush.data.model.ApiResponse
import bose.ankush.data.model.UserRole
import com.auth0.jwt.interfaces.DecodedJWT
import config.JwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Unified JWT authentication helper to standardize authentication across all endpoints. This
 * eliminates inconsistent JWT token handling and provides a single source of truth for
 * authentication logic.
 */
object AuthHelper {
    private val logger = LoggerFactory.getLogger("AuthHelper")

    /** Data class representing an authenticated user */
    data class AuthenticatedUser(
        val email: String,
        val role: UserRole,
        val isActive: Boolean,
        val token: String
    )

    /** Authentication result sealed class */
    sealed class AuthResult {
        data class Success(val user: AuthenticatedUser) : AuthResult()
        data class Failure(val message: String, val statusCode: HttpStatusCode) : AuthResult()
    }

    /**
     * Helper function to respond with error without reified type issues
     */
    private suspend fun ApplicationCall.respondAuthError(
        message: String,
        status: HttpStatusCode
    ) {
        val response = ApiResponse<Unit?>(
            status = false,
            message = message,
            data = null
        )

        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            allowSpecialFloatingPointValues = true
            useArrayPolymorphism = false
        }

        val body = json.encodeToString(ApiResponse.serializer(kotlinx.serialization.serializer()), response)
        respondText(text = body, contentType = ContentType.Application.Json, status = status)
    }

    /**
     * Extract JWT token from request headers or cookies Priority: Authorization header -> jwt_token
     * cookie
     */
    private fun ApplicationCall.extractJwtToken(): String? {
        // First try Authorization header
        val authHeader = request.headers["Authorization"]
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim()
        }

        // Fallback to cookie
        return request.cookies["jwt_token"]?.trim()
    }

    /** Verify and decode JWT token */
    private fun verifyToken(token: String): DecodedJWT? {
        return try {
            JwtConfig.verifier.verify(token)
        } catch (e: Exception) {
            logger.debug("JWT verification failed: ${e.message}")
            null
        }
    }

    /** Extract user information from decoded JWT */
    private fun extractUserFromJWT(decodedJWT: DecodedJWT, token: String): AuthenticatedUser? {
        return try {
            val email = decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
            val roleString = decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_ROLE).asString()
            val isActive = decodedJWT.getClaim("isActive")?.asBoolean() ?: true

            if (email.isNullOrBlank()) {
                logger.warn("JWT token missing email claim")
                return null
            }

            val role =
                try {
                    if (roleString.isNullOrBlank()) {
                        UserRole.USER // Default role
                    } else {
                        UserRole.valueOf(roleString.uppercase())
                    }
                } catch (e: Exception) {
                    logger.warn("Invalid role in JWT: $roleString, defaulting to USER. Issue: ${e.message}")
                    UserRole.USER
                }

            AuthenticatedUser(email = email, role = role, isActive = isActive, token = token)
        } catch (e: Exception) {
            logger.warn("Failed to extract user from JWT: ${e.message}")
            null
        }
    }

    /**
     * Authenticate user from ApplicationCall This method handles both Ktor's built-in
     * authentication and manual token extraction
     */
    fun ApplicationCall.authenticateUser(): AuthResult {
        // First, try to get from Ktor's authentication context (if available)
        val principal = principal<JWTPrincipal>()
        if (principal != null) {
            // Extract user info directly from the payload since we have it
            val email = principal.payload.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
            val roleString = principal.payload.getClaim(Constants.Auth.JWT_CLAIM_ROLE).asString()
            val isActive = principal.payload.getClaim("isActive")?.asBoolean() ?: true

            if (!email.isNullOrBlank()) {
                val role =
                    try {
                        if (roleString.isNullOrBlank()) {
                            UserRole.USER // Default role
                        } else {
                            UserRole.valueOf(roleString.uppercase())
                        }
                    } catch (e: Exception) {
                        logger.warn("Invalid role in JWT: $roleString, defaulting to USER. Issue: ${e.message}")
                        UserRole.USER
                    }

                val user =
                    AuthenticatedUser(
                        email = email,
                        role = role,
                        isActive = isActive,
                        token = "from-principal"
                    )

                if (user.isActive) {
                    return AuthResult.Success(user)
                }
            }
        }

        // Manual token extraction and verification
        val token = extractJwtToken()
        if (token.isNullOrBlank()) {
            return AuthResult.Failure(
                "Authentication required. Please provide a valid JWT token.",
                HttpStatusCode.Unauthorized
            )
        }

        val decodedJWT = verifyToken(token)
        if (decodedJWT == null) {
            return AuthResult.Failure(
                "Invalid or expired token. Please login again.",
                HttpStatusCode.Unauthorized
            )
        }

        val user = extractUserFromJWT(decodedJWT, token)
        if (user == null) {
            return AuthResult.Failure(
                "Invalid token format. Please login again.",
                HttpStatusCode.Unauthorized
            )
        }

        if (!user.isActive) {
            return AuthResult.Failure(
                "Account is inactive. Please contact support.",
                HttpStatusCode.Forbidden
            )
        }

        return AuthResult.Success(user)
    }

    /** Authenticate user and ensure they have the required role */
    fun ApplicationCall.authenticateUserWithRole(requiredRole: UserRole): AuthResult {
        return when (val authResult = authenticateUser()) {
            is AuthResult.Success -> {
                if (authResult.user.role == requiredRole || authResult.user.role == UserRole.ADMIN
                ) {
                    authResult
                } else {
                    AuthResult.Failure(
                        "Insufficient privileges. ${requiredRole.name} role required.",
                        HttpStatusCode.Forbidden
                    )
                }
            }

            is AuthResult.Failure -> authResult
        }
    }

    /** Authenticate admin user */
    fun ApplicationCall.authenticateAdmin(): AuthResult {
        return authenticateUserWithRole(UserRole.ADMIN)
    }

    /**
     * Extension function to get authenticated user or respond with error This is the main function
     * that routes should use
     */
    suspend fun ApplicationCall.getAuthenticatedUserOrRespond(): AuthenticatedUser? {
        return when (val result = authenticateUser()) {
            is AuthResult.Success -> {
                logger.debug("User authenticated: ${result.user.email}")
                result.user
            }

            is AuthResult.Failure -> {
                logger.info("Authentication failed: ${result.message}")
                respondAuthError(result.message, result.statusCode)
                null
            }
        }
    }

    /** Extension function to get authenticated admin user or respond with error */
    suspend fun ApplicationCall.getAuthenticatedAdminOrRespond(): AuthenticatedUser? {
        return when (val result = authenticateAdmin()) {
            is AuthResult.Success -> {
                logger.debug("Admin authenticated: ${result.user.email}")
                result.user
            }

            is AuthResult.Failure -> {
                logger.info("Admin authentication failed: ${result.message}")
                respondAuthError(result.message, result.statusCode)
                null
            }
        }
    }

    /**
     * Check if a JWT token is valid without extracting user info Useful for token validation
     * endpoints
     */
    fun isTokenValid(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return verifyToken(token) != null
    }

    /** Check if a user has an admin role from JWT token */
    fun isAdminToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val decoded = verifyToken(token) ?: return false
        return JwtConfig.isAdmin(decoded)
    }
}
