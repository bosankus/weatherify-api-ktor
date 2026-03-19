package bose.ankush.route

import bose.ankush.data.model.*
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import bose.ankush.util.PasswordUtil
import config.JwtConfig
import domain.model.Result
import domain.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import util.Constants
import java.time.Instant

fun Route.authRoute() {
    val userRepository: UserRepository by application.inject()
    val analytics: util.Analytics by application.inject()
    val logger = LoggerFactory.getLogger("AuthRoute")

    post(Constants.Api.REGISTER_ENDPOINT) {
        call.handleAuth(logger, "registration") {
            val request = call.receive<UserRegistrationRequest>()
            logger.info("Registration request received for email: ${request.email}")

            if (!PasswordUtil.validateEmailFormat(request.email)) {
                call.respondError(Constants.Auth.INVALID_EMAIL_FORMAT, Unit, HttpStatusCode.BadRequest)
                return@handleAuth
            }
            if (!PasswordUtil.validatePasswordStrength(request.password)) {
                call.respondError(Constants.Auth.INVALID_PASSWORD_STRENGTH, Unit, HttpStatusCode.BadRequest)
                return@handleAuth
            }

            when (val result = userRepository.findUserByEmail(request.email)) {
                is Result.Success -> if (result.data != null) {
                    call.respondError(Constants.Messages.USER_ALREADY_EXISTS, Unit, HttpStatusCode.Conflict)
                    return@handleAuth
                }
                is Result.Error -> {
                    call.respondResultError(result.message, "check if user exists")
                    return@handleAuth
                }
            }

            val user = User(
                email = request.email,
                passwordHash = PasswordUtil.hashPassword(request.password),
                timestampOfRegistration = request.timestampOfRegistration,
                deviceModel = request.deviceModel,
                operatingSystem = request.operatingSystem,
                osVersion = request.osVersion,
                appVersion = request.appVersion,
                ipAddress = request.ipAddress,
                registrationSource = request.registrationSource,
                role = request.role,
                isActive = request.isActive,
                isPremium = request.isPremium,
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
            logger.info("Login request received for email: ${request.email}")

            val user = userRepository.findUserByEmail(request.email)
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
            val email = JwtConfig.validateExpiredTokenAndExtractEmail(request.token)

            if (email == null) {
                // Token is either still valid or completely invalid
                try {
                    val decodedJwt = JwtConfig.verifier.verify(request.token)
                    val validEmail = decodedJwt.getClaim("email").asString()
                    logger.info("Token not expired for user: $validEmail")

                    val user = (userRepository.findUserByEmail(validEmail) as? Result.Success)?.data
                    call.respondLoginSuccess(Constants.Messages.TOKEN_NOT_EXPIRED, request.token, user)
                } catch (_: Exception) {
                    logger.warn("Invalid token provided for refresh")
                    call.respondError(
                        Constants.Messages.TOKEN_INVALID,
                        mapOf("errorCode" to "TOKEN_INVALID"),
                        HttpStatusCode.BadRequest
                    )
                }
                return@handleAuth
            }

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

    post(Constants.Api.LOGOUT_ENDPOINT) {
        call.handleAuth(logger, "logout") {
            logger.info("Logout request received")
            analytics.event("logout", emptyMap(), userAgent = call.request.headers["User-Agent"])
            call.performLogout()
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
            if (data == null) {
                call.respondError(Constants.Messages.USER_NOT_REGISTERED, Unit, HttpStatusCode.Unauthorized)
                null
            } else data
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
            Cookie(name = "jwt_token", value = token, path = "/", httpOnly = true, secure = true, maxAge = maxAgeSeconds)
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
        else -> Constants.Messages.DATABASE_ERROR
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

suspend fun ApplicationCall.performLogout() {
    response.cookies.append(
        Cookie(name = "jwt_token", value = "", path = "/", httpOnly = true, secure = true, maxAge = 0)
    )
    respondSuccess<Unit>(Constants.Messages.LOGOUT_SUCCESS, Unit, HttpStatusCode.OK)
}
