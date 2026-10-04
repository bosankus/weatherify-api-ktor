package util

import bose.ankush.data.model.ApiResponse
import com.androidplay.core.common.Result
import com.androidplay.weatherify.domain.UserRole
import com.androidplay.weatherify.repository.UserRepository
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Payload
import config.JwtConfig
import config.SessionDecision
import config.SessionPolicy
import config.SessionPurpose
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

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

    private fun roleFromClaim(roleString: String?): UserRole {
        return try {
            if (roleString.isNullOrBlank()) {
                UserRole.USER
            } else {
                UserRole.valueOf(roleString.uppercase())
            }
        } catch (e: Exception) {
            logger.warn("Invalid role in JWT: $roleString, defaulting to USER. Issue: ${e.message}")
            UserRole.USER
        }
    }

    private fun extractUserFromJWT(decodedJWT: DecodedJWT, token: String): AuthenticatedUser? {
        return try {
            val email = decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
            val roleString = decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_ROLE).asString()
            // isActive is not read from the token. The user document is the source of truth.

            if (email.isNullOrBlank()) {
                logger.warn("JWT token missing email claim")
                return null
            }

            AuthenticatedUser(
                email = email,
                role = roleFromClaim(roleString),
                isActive = true,
                token = token
            )
        } catch (e: Exception) {
            logger.warn("Failed to extract user from JWT: ${e.message}")
            null
        }
    }

    /**
     * Signature is already checked. Load the user and apply [SessionPolicy].
     * Role stays the JWT role. isActive and session generation come from the document.
     */
    private suspend fun ApplicationCall.authorizeStoredUser(
        email: String,
        role: UserRole,
        token: String,
        tokenGeneration: Int?,
    ): AuthResult {
        val userRepository = try {
            application.get<UserRepository>()
        } catch (e: Exception) {
            logger.error("User repository unavailable during authentication: ${e.message}")
            return AuthResult.Failure(
                "Authentication failed. Please try again.",
                HttpStatusCode.InternalServerError
            )
        }
        return when (val result = userRepository.findUserByEmail(email)) {
            is Result.Error -> {
                logger.error("Failed to load user during authentication: ${result.message}")
                AuthResult.Failure(
                    "Authentication failed. Please try again.",
                    HttpStatusCode.InternalServerError
                )
            }
            is Result.Success -> when (
                SessionPolicy.evaluate(
                    userPresent = result.data != null,
                    isActive = result.data?.isActive == true,
                    storedGeneration = result.data?.sessionGeneration,
                    tokenGeneration = tokenGeneration,
                    purpose = SessionPurpose.AUTHENTICATED_CALL,
                    tokenExpired = false,
                )
            ) {
                SessionDecision.ALLOW -> AuthResult.Success(
                    AuthenticatedUser(
                        email = email,
                        role = role,
                        isActive = true,
                        token = token
                    )
                )
                SessionDecision.REJECT_INACTIVE -> AuthResult.Failure(
                    "Account is inactive. Please contact support.",
                    HttpStatusCode.Forbidden
                )
                else -> AuthResult.Failure(
                    "Invalid or expired token. Please login again.",
                    HttpStatusCode.Unauthorized
                )
            }
        }
    }

    /** True when this already-verified token may still call authenticated routes. */
    suspend fun ApplicationCall.sessionStillValid(payload: Payload): Boolean {
        val email = payload.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
        if (email.isNullOrBlank()) return false
        val role = roleFromClaim(payload.getClaim(Constants.Auth.JWT_CLAIM_ROLE).asString())
        return authorizeStoredUser(
            email = email,
            role = role,
            token = "checked",
            tokenGeneration = JwtConfig.sessionGeneration(payload),
        ) is AuthResult.Success
    }

    suspend fun ApplicationCall.authenticateUser(): AuthResult {
        val principal = principal<JWTPrincipal>()
        if (principal != null) {
            val email = principal.payload.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
            if (!email.isNullOrBlank()) {
                val role = roleFromClaim(
                    principal.payload.getClaim(Constants.Auth.JWT_CLAIM_ROLE).asString()
                )
                return authorizeStoredUser(
                    email = email,
                    role = role,
                    token = "from-principal",
                    tokenGeneration = JwtConfig.sessionGeneration(principal.payload),
                )
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

        return authorizeStoredUser(
            email = user.email,
            role = user.role,
            token = token,
            tokenGeneration = JwtConfig.sessionGeneration(decodedJWT),
        )
    }

    suspend fun ApplicationCall.authenticateUserWithRole(requiredRole: UserRole): AuthResult {
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

    suspend fun ApplicationCall.authenticateAdmin(): AuthResult {
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
