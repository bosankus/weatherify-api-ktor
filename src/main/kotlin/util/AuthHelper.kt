package util

import bose.ankush.data.model.ApiResponse
import com.androidplay.weatherify.domain.UserRole
import com.auth0.jwt.interfaces.DecodedJWT
import config.JwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest

object AuthHelper {
    private val logger = LoggerFactory.getLogger("AuthHelper")
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = false
    }

    data class AuthenticatedUser(
        val email: String,
        val role: UserRole,
        val isActive: Boolean,
        val token: String
    )

    sealed class AuthResult {
        data class Success(val user: AuthenticatedUser) : AuthResult()
        data class Failure(val message: String, val statusCode: HttpStatusCode) : AuthResult()
    }

    private suspend fun ApplicationCall.respondAuthError(message: String, status: HttpStatusCode) {
        val response = ApiResponse<Unit?>(status = false, message = message, data = null)
        val body = json.encodeToString(ApiResponse.serializer(kotlinx.serialization.serializer()), response)
        respondText(text = body, contentType = ContentType.Application.Json, status = status)
    }

    private fun ApplicationCall.extractJwtToken(): String? {
        val authHeader = request.headers["Authorization"]
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim()
        }
        return request.cookies["jwt_token"]?.trim()
    }

    private fun verifyToken(token: String): DecodedJWT? {
        return try {
            JwtConfig.verifier.verify(token)
        } catch (e: Exception) {
            logger.debug("JWT verification failed: ${e.message}")
            null
        }
    }

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

    fun ApplicationCall.authenticateUser(): AuthResult {
        val principal = principal<JWTPrincipal>()
        if (principal != null) {
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

    fun ApplicationCall.authenticateAdmin(): AuthResult {
        return authenticateUserWithRole(UserRole.ADMIN)
    }

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


    // Bot shared-secret gate for /bot/atlassian/... (APE-10).
    // Accepts Authorization: Bearer <secret> or X-Bot-Token: <secret>.
    // Constant-time comparison; responds 401 when missing/wrong.
    suspend fun ApplicationCall.requireBotSharedSecretOrRespond(expectedSecret: String): Boolean {
        if (expectedSecret.isBlank() || expectedSecret == DUMMY_BOT_SHARED_SECRET) {
            logger.error("Bot shared secret is not configured")
            respondAuthError(
                "Bot authentication is not configured on this server.",
                HttpStatusCode.Unauthorized,
            )
            return false
        }
        val provided = extractBotSharedSecret()
        if (provided.isNullOrBlank() || !constantTimeEquals(provided, expectedSecret)) {
            logger.info("Bot shared-secret authentication failed")
            respondAuthError(
                "Bot authentication required. Provide Authorization Bearer or X-Bot-Token.",
                HttpStatusCode.Unauthorized,
            )
            return false
        }
        return true
    }

    private fun ApplicationCall.extractBotSharedSecret(): String? {
        val authHeader = request.headers["Authorization"]
        if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            val token = authHeader.substring(7).trim()
            if (token.isNotEmpty()) return token
        }
        return request.headers["X-Bot-Token"]?.trim()?.takeIf { it.isNotEmpty() }
    }

    private const val DUMMY_BOT_SHARED_SECRET = "dummy_bot_atlassian_shared_secret"

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }

    fun isTokenValid(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return verifyToken(token) != null
    }

    fun isAdminToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val decoded = verifyToken(token) ?: return false
        return JwtConfig.isAdmin(decoded)
    }
}
