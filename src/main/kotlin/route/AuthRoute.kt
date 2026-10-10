package bose.ankush.route

import com.androidplay.weatherify.domain.*
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import bose.ankush.util.PasswordUtil
import config.JwtConfig
import config.TokenRefreshResult
import com.androidplay.core.common.Result
import com.androidplay.weatherify.repository.SavedLocationRepository
import com.androidplay.weatherify.repository.UserRepository
import domain.service.WeatherAggregatorService
import domain.service.live.LiveEntitlementResolver
import kotlinx.serialization.Serializable
import util.ProfilePhotoActions
import util.ProfilePhotoStorage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.*
import io.ktor.server.routing.*
import bose.ankush.base.AUTH_RATE_LIMIT
import bose.ankush.base.UserStatusGate
import bose.ankush.base.clientIp
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import util.Constants
import java.time.Instant

fun Route.authRoute() {
    val userRepository: UserRepository by application.inject()
    val analytics: util.Analytics by application.inject()
    val savedLocationRepository: SavedLocationRepository by application.inject()
    val weatherAggregatorService: WeatherAggregatorService by application.inject()
    val liveEntitlementResolver: LiveEntitlementResolver by application.inject()
    val photoStorage: ProfilePhotoStorage by application.inject()
    val photoActions = ProfilePhotoActions(photoStorage, userRepository)
    val logger = LoggerFactory.getLogger("AuthRoute")

    rateLimit(AUTH_RATE_LIMIT) {

    post(Constants.Api.REGISTER_ENDPOINT) {
        call.handleAuth(logger, "registration") {
            val request = call.receive<UserRegistrationRequest>()
            val email = request.email.lowercase().trim()
            logger.info("Registration request received for email: $email")

            if (!PasswordUtil.validateEmailFormat(email)) {
                call.respondError(Constants.Auth.INVALID_EMAIL_FORMAT, Unit, HttpStatusCode.BadRequest)
                return@handleAuth
            }
            if (!PasswordUtil.validatePasswordStrength(request.password)) {
                call.respondError(Constants.Auth.INVALID_PASSWORD_STRENGTH, Unit, HttpStatusCode.BadRequest)
                return@handleAuth
            }

            when (val result = userRepository.findUserByEmail(email)) {
                is Result.Success -> {
                    val existing = result.data
                    if (existing != null && existing.deletedAt == null) {
                        call.respondError(Constants.Messages.USER_ALREADY_EXISTS, Unit, HttpStatusCode.Conflict)
                        return@handleAuth
                    }
                    if (existing != null) {
                        // Tombstone from a previously deleted account: replace it with the fresh registration.
                        logger.info("Replacing deleted-account tombstone for $email on re-registration")
                        userRepository.deleteUserByEmail(email)
                            .unwrapOrRespondError(call, "clear deleted account") ?: return@handleAuth
                    }
                }
                is Result.Error -> {
                    call.respondResultError(result.message, "check if user exists")
                    return@handleAuth
                }
            }

            val ipAddress = call.clientIp()

            val user = User(
                email = email,
                passwordHash = PasswordUtil.hashPassword(request.password),
                timestampOfRegistration = request.timestampOfRegistration,
                deviceModel = request.deviceModel,
                operatingSystem = request.operatingSystem,
                osVersion = request.osVersion,
                appVersion = request.appVersion,
                ipAddress = ipAddress,
                registrationSource = request.registrationSource,
                role = UserRole.USER,
                isActive = true,
                isPremium = false,
                fcmToken = request.firebaseToken
            )

            val created = userRepository.createUser(user)
                .unwrapOrRespondError(call, "register user") ?: return@handleAuth
            if (!created) {
                call.respondError(Constants.Messages.FAILED_REGISTER, Unit, HttpStatusCode.InternalServerError)
                return@handleAuth
            }

            logger.info("User registered successfully: ${request.email}")
            val token = JwtConfig.generateToken(user.email, user.role)
            call.setAuthCookie(token, logger)
            analytics.event("sign_up", mapOf("method" to "email_password"), user.email, call.request.headers["User-Agent"])
            call.respondLoginSuccess(Constants.Messages.LOGIN_SUCCESS, token, user)
        }
    }

    post(Constants.Api.LOGIN_ENDPOINT) {
        call.handleAuth(logger, "login") {
            val request = call.receive<UserLoginRequest>()
            val email = request.email.lowercase().trim()
            logger.info("Login request received for email: $email")

            val user = userRepository.findUserByEmail(email)
                .requireUser(call, "find user") ?: return@handleAuth

            if (!PasswordUtil.verifyPassword(request.password, user.passwordHash)) {
                call.respondError(Constants.Messages.INVALID_CREDENTIALS, Unit, HttpStatusCode.Unauthorized)
                return@handleAuth
            }
            if (!user.isActive) {
                call.respondError(Constants.Messages.ACCOUNT_INACTIVE, Unit, HttpStatusCode.Forbidden)
                return@handleAuth
            }

            val token = JwtConfig.generateToken(user.email, user.role)
            logger.info("Login successful for user: ${request.email}")
            call.setAuthCookie(token, logger)
            analytics.event("login", mapOf("method" to "email_password"), user.email, call.request.headers["User-Agent"])
            call.respondLoginSuccess(Constants.Messages.LOGIN_SUCCESS, token, user)
        }
    }

    post(Constants.Api.REFRESH_TOKEN_ENDPOINT) {
        call.handleAuth(logger, "token refresh") {
            val contentType = call.request.contentType()
            if (!contentType.match(ContentType.Application.Json)) {
                call.respondError(
                    "${Constants.Messages.VALIDATION_ERROR}: Content-Type must be application/json",
                    mapOf("error" to "Invalid Content-Type", "received" to contentType.toString()),
                    HttpStatusCode.BadRequest
                )
                return@handleAuth
            }

            val request = try {
                call.receive<TokenRefreshRequest>()
            } catch (e: Exception) {
                call.respondError(
                    "${Constants.Messages.VALIDATION_ERROR}: Invalid request body. Expected JSON with 'token' field.",
                    mapOf("error" to "Request body parsing failed", "details" to (e.message ?: "Unknown error")),
                    HttpStatusCode.BadRequest
                )
                return@handleAuth
            }

            if (request.token.isBlank()) {
                call.respondError(
                    "${Constants.Messages.VALIDATION_ERROR}: Token field is required and cannot be empty.",
                    Unit, HttpStatusCode.BadRequest
                )
                return@handleAuth
            }

            logger.info("Token refresh request received")
            when (val refreshResult = JwtConfig.checkTokenForRefresh(request.token)) {
                is TokenRefreshResult.Invalid -> {
                    logger.warn("Invalid token provided for refresh")
                    call.respondError(
                        Constants.Messages.TOKEN_INVALID,
                        mapOf("errorCode" to "TOKEN_INVALID"),
                        HttpStatusCode.BadRequest
                    )
                }
                is TokenRefreshResult.StillValid -> {
                    logger.info("Token not expired for user: ${refreshResult.email}")
                    val user = (userRepository.findUserByEmail(refreshResult.email) as? Result.Success)?.data
                    if (user?.deletedAt != null) {
                        call.respondError(Constants.Messages.USER_NOT_REGISTERED, Unit, HttpStatusCode.Unauthorized)
                        return@handleAuth
                    }
                    call.respondLoginSuccess(Constants.Messages.TOKEN_NOT_EXPIRED, request.token, user)
                }
                is TokenRefreshResult.Expired -> {
                    val email = refreshResult.email
                    val user = userRepository.findUserByEmail(email)
                        .requireUser(call, "find user during token refresh") ?: return@handleAuth
                    if (!user.isActive) {
                        call.respondError(Constants.Messages.ACCOUNT_INACTIVE, Unit, HttpStatusCode.Forbidden)
                        return@handleAuth
                    }
                    val newToken = JwtConfig.generateToken(email, user.role)
                    logger.info("Token refreshed successfully for user: $email")
                    analytics.event("token_refresh", emptyMap(), email, call.request.headers["User-Agent"])
                    call.respondLoginSuccess(Constants.Messages.TOKEN_REFRESH_SUCCESS, newToken, user)
                }
            }
        }
    }

    } // end rateLimit(AUTH_RATE_LIMIT)

    authenticate("jwt-auth") {
        // Self-service account deletion (required by Play Store data-deletion policy).
        // Soft delete: the user document is kept as a deactivated tombstone with deletedAt set,
        // and payment/refund records are retained for financial record-keeping.
        rateLimit(AUTH_RATE_LIMIT) {
            delete(Constants.Api.DELETE_ACCOUNT_ENDPOINT) {
                call.handleAuth(logger, "account deletion") {
                    val email = call.principal<JWTPrincipal>()
                        ?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()
                        ?.lowercase()?.trim()
                    if (email.isNullOrEmpty()) {
                        call.respondError("Invalid authentication token", Unit, HttpStatusCode.Unauthorized)
                        return@handleAuth
                    }

                    val request = try {
                        call.receive<DeleteAccountRequest>()
                    } catch (_: Exception) {
                        call.respondError(
                            "${Constants.Messages.VALIDATION_ERROR}: Request body must contain 'password'",
                            Unit, HttpStatusCode.BadRequest
                        )
                        return@handleAuth
                    }

                    val user = userRepository.findUserByEmail(email)
                        .requireUser(call, "find user for deletion") ?: return@handleAuth

                    if (!PasswordUtil.verifyPassword(request.password, user.passwordHash)) {
                        // 403, not 401: the session is fine, only the confirmation is wrong. A 401
                        // would make clients try a token refresh or log the user out.
                        call.respondError(
                            "Incorrect password",
                            mapOf("errorCode" to "INVALID_PASSWORD"),
                            HttpStatusCode.Forbidden
                        )
                        return@handleAuth
                    }

                    // Dependent data first, tombstoning last, so a failure leaves a retryable account.
                    savedLocationRepository.deleteAllLocationsByUser(email)
                        .unwrapOrRespondError(call, "delete saved locations") ?: return@handleAuth
                    if (!user.photoObject.isNullOrBlank()) {
                        photoActions.delete(email)
                            .unwrapOrRespondError(call, "delete profile photo") ?: return@handleAuth
                    }
                    userRepository.markUserDeleted(email, Instant.now().toString())
                        .unwrapOrRespondError(call, "delete user") ?: return@handleAuth

                    weatherAggregatorService.invalidateUserCache(email)
                    liveEntitlementResolver.invalidate(email)
                    UserStatusGate.invalidate(email)
                    logger.info("Account deleted for user: $email")
                    analytics.event("account_deleted", emptyMap(), email, call.request.headers["User-Agent"])
                    call.performLogout(Constants.Messages.ACCOUNT_DELETED)
                }
            }
        }

        post(Constants.Api.LOGOUT_ENDPOINT) {
            call.handleAuth(logger, "logout") {
                val email = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()
                logger.info("Logout request received for user: $email")
                analytics.event("logout", emptyMap(), email, call.request.headers["User-Agent"])
                call.performLogout()
            }
        }
    }
}

// -- Private helpers --

/**
 * Unwraps a Result<T>, responding with a classified error on failure.
 * Returns the data on success, or null after sending an error response.
 */
private suspend fun <T> Result<T>.unwrapOrRespondError(
    call: ApplicationCall,
    context: String
): T? {
    return when (this) {
        is Result.Success -> data
        is Result.Error -> {
            call.respondResultError(message, context)
            null
        }
    }
}

/**
 * Unwraps a Result<User?>, requiring the user to exist.
 * Returns the User on success, responds with appropriate error and returns null otherwise.
 */
private suspend fun Result<User?>.requireUser(
    call: ApplicationCall,
    context: String
): User? {
    return when (this) {
        is Result.Success -> {
            val user = data
            if (user == null || user.deletedAt != null) {
                call.respondError(Constants.Messages.USER_NOT_REGISTERED, Unit, HttpStatusCode.Unauthorized)
                null
            } else user
        }
        is Result.Error -> {
            call.respondResultError(message, context)
            null
        }
    }
}

/** Responds with a classified error from a Result.Error message. */
private suspend fun ApplicationCall.respondResultError(message: String, context: String) {
    respondError(
        "${classifyError(message)}: Failed to $context - $message",
        mapOf("errorType" to classifyError(message).substringBefore(":")),
        HttpStatusCode.InternalServerError
    )
}

/** Wraps a route handler with consistent exception handling and error classification. */
private suspend fun ApplicationCall.handleAuth(
    logger: Logger,
    endpoint: String,
    block: suspend () -> Unit
) {
    try {
        block()
    } catch (e: Exception) {
        logger.error("Exception during $endpoint: ${e.message}", e)
        val errorType = classifyException(e)
        try {
            respondError(
                "$errorType: ${e.message}",
                mapOf("errorType" to errorType.substringBefore(":"), "errorClass" to e.javaClass.simpleName),
                HttpStatusCode.InternalServerError
            )
        } catch (re: Exception) {
            logger.error("Failed to send error response: ${re.message}", re)
            respondError("Internal server error", mapOf("error" to "Failed to process request"), HttpStatusCode.InternalServerError)
        }
    }
}

/** Builds a LoginResponse from a User (or defaults if null) and responds with success. */
private suspend fun ApplicationCall.respondLoginSuccess(message: String, token: String, user: User?) {
    val effectivePremium = user != null && user.isPremium &&
        user.premiumExpiresAt != null &&
        Instant.parse(user.premiumExpiresAt).isAfter(Instant.now())

    respondSuccess(
        message,
        LoginResponse(
            token = token,
            email = user?.email ?: "",
            role = user?.role ?: UserRole.USER,
            isActive = user?.isActive ?: true,
            isPremium = effectivePremium,
            premiumExpiresAt = if (effectivePremium) user.premiumExpiresAt else null
        ),
        HttpStatusCode.OK
    )
}

private fun ApplicationCall.setAuthCookie(token: String, logger: Logger) {
    try {
        val maxAgeSeconds = (config.Environment.getJwtExpiration() / 1000).toInt()
        response.cookies.append(
            Cookie(
                name = "jwt_token",
                value = token,
                path = "/",
                httpOnly = true,
                secure = true,
                maxAge = maxAgeSeconds,
                extensions = mapOf("SameSite" to "Strict")
            )
        )
    } catch (e: Exception) {
        logger.warn("Failed to set auth cookie: ${e.message}")
    }
}

private fun classifyError(message: String): String {
    val msg = message.lowercase()
    return when {
        "database" in msg || "mongo" in msg -> Constants.Messages.DATABASE_ERROR
        "validation" in msg -> Constants.Messages.VALIDATION_ERROR
        "network" in msg || "connection" in msg -> Constants.Messages.NETWORK_ERROR
        "auth" in msg || "token" in msg -> Constants.Messages.AUTHENTICATION_ERROR
        else -> Constants.Messages.UNKNOWN_ERROR
    }
}

private fun classifyException(e: Exception): String {
    if (e is IllegalArgumentException) return Constants.Messages.VALIDATION_ERROR
    val msg = (e.message ?: "").lowercase()
    return when {
        "database" in msg || "mongo" in msg -> Constants.Messages.DATABASE_ERROR
        "validation" in msg || "password" in msg || "email" in msg -> Constants.Messages.VALIDATION_ERROR
        "network" in msg || "connection" in msg -> Constants.Messages.NETWORK_ERROR
        "auth" in msg || "token" in msg || "secret" in msg -> Constants.Messages.AUTHENTICATION_ERROR
        else -> Constants.Messages.UNKNOWN_ERROR
    }
}

private suspend fun ApplicationCall.performLogout(message: String = Constants.Messages.LOGOUT_SUCCESS) {
    response.cookies.append(
        Cookie(
            name = "jwt_token",
            value = "",
            path = "/",
            httpOnly = true,
            secure = true,
            maxAge = 0,
            extensions = mapOf("SameSite" to "Strict")
        )
    )
    respondSuccess<Unit>(message, Unit, HttpStatusCode.OK)
}

@Serializable
data class DeleteAccountRequest(val password: String)
