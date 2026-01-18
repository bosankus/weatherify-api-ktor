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
import org.slf4j.LoggerFactory
import util.Constants

/**
 * Authentication routes for user registration, login, and token refresh
 */
fun Route.authRoute() {
    val userRepository: UserRepository by application.inject()
    val analytics: util.Analytics by application.inject()
    val logger = LoggerFactory.getLogger("AuthRoute")

    /**
     * Register a new user
     * POST /register with email and password in request body
     * Returns 201 Created on success, various error codes on failure
     */
    post(Constants.Api.REGISTER_ENDPOINT) {
        try {
            // Parse request body
            val request = call.receive<UserRegistrationRequest>()
            logger.info("Registration request received for email: ${request.email}")

            // Validate email format
            if (!PasswordUtil.validateEmailFormat(request.email)) {
                logger.warn("Invalid email format: ${request.email}")
                call.respondError(
                    Constants.Auth.INVALID_EMAIL_FORMAT,
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            // Validate password strength
            if (!PasswordUtil.validatePasswordStrength(request.password)) {
                logger.warn("Invalid password strength for email: ${request.email}")
                call.respondError(
                    Constants.Auth.INVALID_PASSWORD_STRENGTH,
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            // Check if user already exists
            when (val existingUserResult = userRepository.findUserByEmail(request.email)) {
                is Result.Success -> {
                    if (existingUserResult.data != null) {
                        logger.warn("User already exists: ${request.email}")
                        call.respondError(
                            Constants.Messages.USER_ALREADY_EXISTS,
                            Unit,
                            HttpStatusCode.Conflict
                        )
                        return@post
                    }
                }

                is Result.Error -> {
                    logger.error("Error checking if user exists: ${existingUserResult.message}")

                    // Determine the type of error based on the message
                    val errorMessage = when {
                        existingUserResult.message.contains(
                            "database",
                            ignoreCase = true
                        ) -> Constants.Messages.DATABASE_ERROR

                        existingUserResult.message.contains(
                            "validation",
                            ignoreCase = true
                        ) -> Constants.Messages.VALIDATION_ERROR

                        existingUserResult.message.contains(
                            "network",
                            ignoreCase = true
                        ) -> Constants.Messages.NETWORK_ERROR

                        existingUserResult.message.contains(
                            "auth",
                            ignoreCase = true
                        ) -> Constants.Messages.AUTHENTICATION_ERROR

                        else -> Constants.Messages.DATABASE_ERROR // Most likely a database error in this context
                    }

                    call.respondError(
                        "$errorMessage: Failed to check if user exists - ${existingUserResult.message}",
                        mapOf("errorType" to errorMessage.substringBefore(":")),
                        HttpStatusCode.InternalServerError
                    )
                    return@post
                }
            }

            // Hash password and create user
            val passwordHash = PasswordUtil.hashPassword(request.password)
            val user = User(
                email = request.email,
                passwordHash = passwordHash,
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

            // Save user to database
            when (val createResult = userRepository.createUser(user)) {
                is Result.Success -> {
                    if (createResult.data) {
                        logger.info("User registered successfully: ${request.email}")

                        // Generate JWT token similar to /login
                        val token = JwtConfig.generateToken(user.email, user.role)

                        // Try to set auth cookie
                        try {
                            val maxAgeSeconds =
                                (config.Environment.getJwtExpiration() / 1000).toInt()
                            call.response.cookies.append(
                                Cookie(
                                    name = "jwt_token",
                                    value = token,
                                    path = "/",
                                    httpOnly = true,
                                    secure = true,
                                    maxAge = maxAgeSeconds
                                )
                            )
                        } catch (e: Exception) {
                            logger.warn("Failed to set auth cookie on register: ${e.message}")
                        }

                        // Analytics: sign_up success
                        analytics.event(
                            name = "sign_up",
                            params = mapOf(
                                "method" to "email_password"
                            ),
                            userId = user.email,
                            userAgent = call.request.headers["User-Agent"]
                        )

                        // Respond like /login
                        call.respondSuccess(
                            Constants.Messages.LOGIN_SUCCESS,
                            LoginResponse(
                                token = token,
                                email = user.email,
                                role = user.role,
                                isActive = user.isActive,
                                isPremium = user.isPremium
                            ),
                            HttpStatusCode.OK
                        )
                    } else {
                        logger.error("Failed to register user: ${request.email}")
                        call.respondError(
                            Constants.Messages.FAILED_REGISTER,
                            Unit,
                            HttpStatusCode.InternalServerError
                        )
                    }
                }

                is Result.Error -> {
                    logger.error("Error registering user: ${createResult.message}")

                    // Determine the type of error based on the message
                    val errorMessage = when {
                        createResult.message.contains(
                            "database",
                            ignoreCase = true
                        ) -> Constants.Messages.DATABASE_ERROR

                        createResult.message.contains(
                            "validation",
                            ignoreCase = true
                        ) -> Constants.Messages.VALIDATION_ERROR

                        createResult.message.contains(
                            "network",
                            ignoreCase = true
                        ) -> Constants.Messages.NETWORK_ERROR

                        createResult.message.contains(
                            "auth",
                            ignoreCase = true
                        ) -> Constants.Messages.AUTHENTICATION_ERROR

                        else -> Constants.Messages.DATABASE_ERROR // Most likely a database error in this context
                    }

                    call.respondError(
                        "$errorMessage: Failed to register user - ${createResult.message}",
                        mapOf("errorType" to errorMessage.substringBefore(":")),
                        HttpStatusCode.InternalServerError
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Exception during registration: ${e.message}", e)

            // Print stack trace for better debugging
            e.printStackTrace()

            // Determine the type of error based on the exception
            val errorMessage = when {
                e is IllegalArgumentException -> Constants.Messages.VALIDATION_ERROR
                e.message?.contains(
                    "database",
                    ignoreCase = true
                ) == true -> Constants.Messages.DATABASE_ERROR

                e.message?.contains(
                    "validation",
                    ignoreCase = true
                ) == true -> Constants.Messages.VALIDATION_ERROR

                e.message?.contains(
                    "network",
                    ignoreCase = true
                ) == true -> Constants.Messages.NETWORK_ERROR

                e.message?.contains(
                    "auth",
                    ignoreCase = true
                ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains(
                    "password",
                    ignoreCase = true
                ) == true -> Constants.Messages.VALIDATION_ERROR

                e.message?.contains(
                    "email",
                    ignoreCase = true
                ) == true -> Constants.Messages.VALIDATION_ERROR

                e.message?.contains(
                    "secret",
                    ignoreCase = true
                ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains(
                    "token",
                    ignoreCase = true
                ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains(
                    "mongo",
                    ignoreCase = true
                ) == true -> Constants.Messages.DATABASE_ERROR

                e.message?.contains(
                    "connection",
                    ignoreCase = true
                ) == true -> Constants.Messages.NETWORK_ERROR

                else -> Constants.Messages.UNKNOWN_ERROR
            }

            // Create a more detailed error response
            val errorDetails = mapOf(
                "errorType" to errorMessage.substringBefore(":"),
                "errorMessage" to e.message,
                "errorClass" to e.javaClass.simpleName
            )

            try {
                call.respondError(
                    "$errorMessage: ${e.message}",
                    errorDetails,
                    HttpStatusCode.InternalServerError
                )
            } catch (respondException: Exception) {
                // If responding with error details fails, try a simpler response
                logger.error(
                    "Failed to send error response: ${respondException.message}",
                    respondException
                )
                call.respondError(
                    "Internal server error",
                    mapOf("error" to "Failed to process request"),
                    HttpStatusCode.InternalServerError
                )
            }
        }
    }

    /**
     * Login a user
     * POST /login with email and password in request body
     * Returns 200 OK with JWT token on success, various error codes on failure
     */
    post(Constants.Api.LOGIN_ENDPOINT) {
        try {
            // Parse request body
            val request = call.receive<UserLoginRequest>()
            logger.info("Login request received for email: ${request.email}")

            // Find user by email
            when (val userResult = userRepository.findUserByEmail(request.email)) {
                is Result.Success -> {
                    val user = userResult.data
                    if (user == null) {
                        logger.warn("User not found: ${request.email}")
                        call.respondError(
                            Constants.Messages.USER_NOT_REGISTERED,
                            Unit,
                            HttpStatusCode.Unauthorized
                        )
                        return@post
                    }

                    // Verify password
                    if (!PasswordUtil.verifyPassword(request.password, user.passwordHash)) {
                        logger.warn("Invalid password for user: ${request.email}")
                        call.respondError(
                            Constants.Messages.INVALID_CREDENTIALS,
                            Unit,
                            HttpStatusCode.Unauthorized
                        )
                        return@post
                    }

                    // Check if user is active
                    if (!user.isActive) {
                        logger.warn("Account is inactive: ${request.email}")
                        call.respondError(
                            Constants.Messages.ACCOUNT_INACTIVE,
                            Unit,
                            HttpStatusCode.Forbidden
                        )
                        return@post
                    }

                    // Generate JWT token with user's role (safe)
                    val token = JwtConfig.generateToken(user.email, user.role)
                    logger.info("Login successful for user: ${request.email}")

                    // Also set auth cookie for browser navigations to protected HTML pages
                    try {
                        val maxAgeSeconds = (config.Environment.getJwtExpiration() / 1000).toInt()
                        call.response.cookies.append(
                            Cookie(
                                name = "jwt_token",
                                value = token,
                                path = "/",
                                httpOnly = true,
                                secure = true,
                                maxAge = maxAgeSeconds
                            )
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to set auth cookie: ${e.message}")
                    }

                    // Analytics: login success
                    analytics.event(
                        name = "login",
                        params = mapOf(
                            "method" to "email_password"
                        ),
                        userId = user.email,
                        userAgent = call.request.headers["User-Agent"]
                    )

                    // Return token and user info in response body
                    call.respondSuccess(
                        Constants.Messages.LOGIN_SUCCESS,
                        LoginResponse(
                            token = token,
                            email = user.email,
                            role = user.role,
                            isActive = user.isActive,
                            isPremium = user.isPremium
                        ),
                        HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    logger.error("Error finding user: ${userResult.message}")

                    // Determine the type of error based on the message
                    val errorMessage = when {
                        userResult.message.contains(
                            "database",
                            ignoreCase = true
                        ) -> Constants.Messages.DATABASE_ERROR

                        userResult.message.contains(
                            "validation",
                            ignoreCase = true
                        ) -> Constants.Messages.VALIDATION_ERROR

                        userResult.message.contains(
                            "network",
                            ignoreCase = true
                        ) -> Constants.Messages.NETWORK_ERROR

                        userResult.message.contains(
                            "auth",
                            ignoreCase = true
                        ) -> Constants.Messages.AUTHENTICATION_ERROR

                        else -> Constants.Messages.DATABASE_ERROR // Most likely a database error in this context
                    }

                    call.respondError(
                        "$errorMessage: Failed to find user - ${userResult.message}",
                        mapOf("errorType" to errorMessage.substringBefore(":")),
                        HttpStatusCode.InternalServerError
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Exception during login: ${e.message}", e)

            // Print stack trace for better debugging
            e.printStackTrace()

            // Determine the type of error based on the exception
            val errorMessage = when {
                e is IllegalArgumentException -> Constants.Messages.VALIDATION_ERROR
                e.message?.contains(
                    "database",
                    ignoreCase = true
                ) == true -> Constants.Messages.DATABASE_ERROR

                e.message?.contains(
                    "validation",
                    ignoreCase = true
                ) == true -> Constants.Messages.VALIDATION_ERROR

                e.message?.contains(
                    "network",
                    ignoreCase = true
                ) == true -> Constants.Messages.NETWORK_ERROR

                e.message?.contains(
                    "auth",
                    ignoreCase = true
                ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains(
                    "password",
                    ignoreCase = true
                ) == true -> Constants.Messages.VALIDATION_ERROR

                e.message?.contains(
                    "email",
                    ignoreCase = true
                ) == true -> Constants.Messages.VALIDATION_ERROR

                e.message?.contains(
                    "secret",
                    ignoreCase = true
                ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains(
                    "token",
                    ignoreCase = true
                ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains(
                    "mongo",
                    ignoreCase = true
                ) == true -> Constants.Messages.DATABASE_ERROR

                e.message?.contains(
                    "connection",
                    ignoreCase = true
                ) == true -> Constants.Messages.NETWORK_ERROR

                else -> Constants.Messages.UNKNOWN_ERROR
            }

            // Create a more detailed error response
            val errorDetails = mapOf(
                "errorType" to errorMessage.substringBefore(":"),
                "errorMessage" to e.message,
                "errorClass" to e.javaClass.simpleName
            )

            try {
                call.respondError(
                    "$errorMessage: ${e.message}",
                    errorDetails,
                    HttpStatusCode.InternalServerError
                )
            } catch (respondException: Exception) {
                // If responding with error details fails, try a simpler response
                logger.error(
                    "Failed to send error response: ${respondException.message}",
                    respondException
                )
                call.respondError(
                    "Internal server error",
                    mapOf("error" to "Failed to process request"),
                    HttpStatusCode.InternalServerError
                )
            }
        }
    }

    /**
     * Refresh an expired JWT token
     * POST /refresh-token with expired token in request body
     * Returns 200 OK with new JWT token on success, various error codes on failure
     */
    post(Constants.Api.REFRESH_TOKEN_ENDPOINT) {
        try {
            // Check Content-Type before attempting to receive
            val contentType = call.request.contentType()
            if (!contentType.match(ContentType.Application.Json)) {
                logger.warn("Refresh token request received without proper Content-Type: $contentType")
                call.respondError(
                    Constants.Messages.VALIDATION_ERROR + ": Content-Type must be application/json",
                    mapOf("error" to "Invalid Content-Type", "received" to contentType.toString()),
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            // Parse request body
            val request = try {
                call.receive<TokenRefreshRequest>()
            } catch (e: io.ktor.server.plugins.CannotTransformContentToTypeException) {
                logger.error("Cannot transform request content to TokenRefreshRequest: ${e.message}", e)
                call.respondError(
                    Constants.Messages.VALIDATION_ERROR + ": Invalid request body. Expected JSON with 'token' field.",
                    mapOf("error" to "Request body parsing failed", "details" to (e.message ?: "Unknown error")),
                    HttpStatusCode.BadRequest
                )
                return@post
            } catch (e: Exception) {
                logger.error("Failed to parse refresh token request: ${e.message}", e)
                call.respondError(
                    Constants.Messages.VALIDATION_ERROR + ": Invalid request body. Expected JSON with 'token' field.",
                    mapOf("error" to "Request body parsing failed", "details" to (e.message ?: "Unknown error")),
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            // Validate token is present
            if (request.token.isBlank()) {
                logger.warn("Refresh token request received with empty token")
                call.respondError(
                    Constants.Messages.VALIDATION_ERROR + ": Token field is required and cannot be empty.",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            logger.info("Token refresh request received")

            // Validate the expired token and extract email
            val email = JwtConfig.validateExpiredTokenAndExtractEmail(request.token)

            if (email == null) {
                // Check if token is still valid (not expired)
                try {
                    JwtConfig.verifier.verify(request.token)
                    logger.warn("Token not expired, refresh rejected")
                    call.respondError(
                        Constants.Messages.TOKEN_NOT_EXPIRED,
                        Unit,
                        HttpStatusCode.BadRequest
                    )
                } catch (_: Exception) {
                    // Token is invalid for some other reason
                    logger.warn("Invalid token provided for refresh")
                    call.respondError(
                        Constants.Messages.TOKEN_INVALID,
                        Unit,
                        HttpStatusCode.BadRequest
                    )
                }
                return@post
            }

            // Find user by email to ensure they still exist and are active
            when (val userResult = userRepository.findUserByEmail(email)) {
                is Result.Success -> {
                    val user = userResult.data
                    if (user == null) {
                        logger.warn("User not found during token refresh: $email")
                        call.respondError(
                            Constants.Messages.USER_NOT_REGISTERED,
                            Unit,
                            HttpStatusCode.Unauthorized
                        )
                        return@post
                    }

                    // Check if user is active
                    if (!user.isActive) {
                        logger.warn("Account is inactive during token refresh: $email")
                        call.respondError(
                            Constants.Messages.ACCOUNT_INACTIVE,
                            Unit,
                            HttpStatusCode.Forbidden
                        )
                        return@post
                    }

                    // Generate new JWT token with user's role (safe)
                    val newToken = JwtConfig.generateToken(email, user.role)
                    logger.info("Token refreshed successfully for user: $email")

                    // Analytics: token_refresh
                    analytics.event(
                        name = "token_refresh",
                        params = emptyMap(),
                        userId = email,
                        userAgent = call.request.headers["User-Agent"]
                    )

                    // Return new token and user info in response body
                    call.respondSuccess(
                        Constants.Messages.TOKEN_REFRESH_SUCCESS,
                        LoginResponse(
                            token = newToken,
                            email = email,
                            role = user.role,
                            isActive = user.isActive,
                            isPremium = user.isPremium
                        ),
                        HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    logger.error("Error finding user during token refresh: ${userResult.message}")

                    // Determine the type of error based on the message
                    val errorMessage = when {
                        userResult.message.contains(
                            "database",
                            ignoreCase = true
                        ) -> Constants.Messages.DATABASE_ERROR

                        userResult.message.contains(
                            "validation",
                            ignoreCase = true
                        ) -> Constants.Messages.VALIDATION_ERROR

                        userResult.message.contains(
                            "network",
                            ignoreCase = true
                        ) -> Constants.Messages.NETWORK_ERROR

                        userResult.message.contains(
                            "auth",
                            ignoreCase = true
                        ) || userResult.message.contains(
                            "token",
                            ignoreCase = true
                        ) -> Constants.Messages.AUTHENTICATION_ERROR

                        else -> Constants.Messages.DATABASE_ERROR // Most likely a database error in this context
                    }

                    call.respondError(
                        "$errorMessage: Failed to find user during token refresh - ${userResult.message}",
                        mapOf("errorType" to errorMessage.substringBefore(":")),
                        HttpStatusCode.InternalServerError
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Exception during token refresh: ${e.message}", e)

            // Print stack trace for better debugging
            e.printStackTrace()

            // Determine the type of error based on the exception
            val errorMessage = when {
                e is IllegalArgumentException -> Constants.Messages.VALIDATION_ERROR
                e.message?.contains(
                    "database",
                    ignoreCase = true
                ) == true -> Constants.Messages.DATABASE_ERROR

                e.message?.contains(
                    "validation",
                    ignoreCase = true
                ) == true -> Constants.Messages.VALIDATION_ERROR

                e.message?.contains(
                    "network",
                    ignoreCase = true
                ) == true -> Constants.Messages.NETWORK_ERROR

                e.message?.contains(
                    "auth",
                    ignoreCase = true
                ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains(
                    "password",
                    ignoreCase = true
                ) == true -> Constants.Messages.VALIDATION_ERROR

                e.message?.contains(
                    "email",
                    ignoreCase = true
                ) == true -> Constants.Messages.VALIDATION_ERROR

                e.message?.contains(
                    "secret",
                    ignoreCase = true
                ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains(
                    "token",
                    ignoreCase = true
                ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains(
                    "mongo",
                    ignoreCase = true
                ) == true -> Constants.Messages.DATABASE_ERROR

                e.message?.contains(
                    "connection",
                    ignoreCase = true
                ) == true -> Constants.Messages.NETWORK_ERROR

                else -> Constants.Messages.UNKNOWN_ERROR
            }

            // Create a more detailed error response
            val errorDetails = mapOf(
                "errorType" to errorMessage.substringBefore(":"),
                "errorMessage" to e.message,
                "errorClass" to e.javaClass.simpleName,
                "endpoint" to "refresh-token"
            )

            try {
                call.respondError(
                    "$errorMessage: ${e.message}",
                    errorDetails,
                    HttpStatusCode.InternalServerError
                )
            } catch (respondException: Exception) {
                // If responding with error details fails, try a simpler response
                logger.error(
                    "Failed to send error response: ${respondException.message}",
                    respondException
                )
                call.respondError(
                    "Internal server error",
                    mapOf("error" to "Failed to process request"),
                    HttpStatusCode.InternalServerError
                )
            }
        }
    }

    /**
     * Logout the current user
     * POST /logout clears auth cookies/session
     */
    post(Constants.Api.LOGOUT_ENDPOINT) {
        try {
            logger.info("Logout request received")
            // Analytics: logout
            analytics.event(
                name = "logout",
                params = emptyMap(),
                userAgent = call.request.headers["User-Agent"]
            )
            call.performLogout()
        } catch (e: Exception) {
            logger.error("Exception during logout: ${e.message}", e)
            e.printStackTrace()

            val errorMessage = when {
                e is IllegalArgumentException -> Constants.Messages.VALIDATION_ERROR
                e.message?.contains("auth", ignoreCase = true) == true ||
                    e.message?.contains(
                        "token",
                        ignoreCase = true
                    ) == true -> Constants.Messages.AUTHENTICATION_ERROR

                e.message?.contains("database", ignoreCase = true) == true ||
                    e.message?.contains(
                        "mongo",
                        ignoreCase = true
                    ) == true -> Constants.Messages.DATABASE_ERROR

                e.message?.contains("connection", ignoreCase = true) == true ||
                    e.message?.contains(
                        "network",
                        ignoreCase = true
                    ) == true -> Constants.Messages.NETWORK_ERROR

                else -> Constants.Messages.UNKNOWN_ERROR
            }

            val errorDetails = mapOf(
                "errorType" to errorMessage.substringBefore(":"),
                "errorMessage" to e.message,
                "errorClass" to e.javaClass.simpleName,
                "endpoint" to "logout"
            )

            try {
                call.respondError(
                    "$errorMessage: ${e.message}",
                    errorDetails,
                    HttpStatusCode.InternalServerError
                )
            } catch (respondException: Exception) {
                logger.error(
                    "Failed to send error response: ${respondException.message}",
                    respondException
                )
                call.respondError(
                    Constants.Messages.INTERNAL_SERVER_ERROR,
                    mapOf("error" to "Failed to process request"),
                    HttpStatusCode.InternalServerError
                )
            }
        }
    }
}

/**
 * Reusable logout operation for clearing auth cookie and responding consistently
 */
suspend fun ApplicationCall.performLogout() {
    // Optionally read token if needed for future blacklist
    // val unusedToken = request.cookies["jwt_token"]

    // Clear the jwt_token cookie
    response.cookies.append(
        Cookie(
            name = "jwt_token",
            value = "",
            path = "/",
            httpOnly = true,
            secure = true,
            maxAge = 0
        )
    )

    // Respond success in a consistent format
    respondSuccess<Unit>(Constants.Messages.LOGOUT_SUCCESS, Unit, HttpStatusCode.OK)
}

