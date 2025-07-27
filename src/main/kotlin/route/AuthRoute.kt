package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory
import bose.ankush.data.model.LoginResponse
import bose.ankush.data.model.User
import bose.ankush.data.model.UserLoginRequest
import bose.ankush.data.model.UserRegistrationRequest
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import bose.ankush.util.PasswordUtil
import config.JwtConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import util.Constants

/**
 * Authentication routes for user registration and login
 */
fun Route.authRoute() {
    /**
     * Register a new user
     * POST /register with email and password in request body
     * Returns 201 Created on success, various error codes on failure
     */
    post(Constants.Api.REGISTER_ENDPOINT) {
        try {
            // Parse request body
            val request = call.receive<UserRegistrationRequest>()

            // Validate email format
            if (!PasswordUtil.validateEmailFormat(request.email)) {
                call.respondError(
                    Constants.Auth.INVALID_EMAIL_FORMAT,
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            // Validate password strength
            if (!PasswordUtil.validatePasswordStrength(request.password)) {
                call.respondError(
                    Constants.Auth.INVALID_PASSWORD_STRENGTH,
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            // Check if user already exists
            val existingUser = DatabaseFactory.findUserByEmail(request.email)
            if (existingUser != null) {
                call.respondError(
                    Constants.Messages.USER_ALREADY_EXISTS,
                    Unit,
                    HttpStatusCode.Conflict
                )
                return@post
            }

            // Hash password and create user
            val passwordHash = PasswordUtil.hashPassword(request.password)
            val user = User(
                email = request.email,
                passwordHash = passwordHash
            )

            // Save user to database
            val success = DatabaseFactory.createUser(user)
            if (success) {
                call.respondSuccess(
                    Constants.Messages.REGISTRATION_SUCCESS,
                    Unit,
                    HttpStatusCode.Created
                )
            } else {
                call.respondError(
                    Constants.Messages.FAILED_REGISTER,
                    Unit,
                    HttpStatusCode.InternalServerError
                )
            }
        } catch (e: Exception) {
            call.respondError(
                "${Constants.Messages.INTERNAL_SERVER_ERROR}: ${e.localizedMessage}",
                Unit,
                HttpStatusCode.InternalServerError
            )
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

            // Find user by email
            val user = DatabaseFactory.findUserByEmail(request.email)
            if (user == null) {
                call.respondError(
                    Constants.Messages.USER_NOT_REGISTERED,
                    Unit,
                    HttpStatusCode.Unauthorized
                )
                return@post
            }

            // Verify password
            if (!PasswordUtil.verifyPassword(request.password, user.passwordHash)) {
                call.respondError(
                    Constants.Messages.INVALID_CREDENTIALS,
                    Unit,
                    HttpStatusCode.Unauthorized
                )
                return@post
            }

            // Check if user is active
            if (!user.isActive) {
                call.respondError(
                    Constants.Messages.ACCOUNT_INACTIVE,
                    Unit,
                    HttpStatusCode.Forbidden
                )
                return@post
            }

            // Generate JWT token
            val token = JwtConfig.generateToken(user.email)

            // Return token in response body
            call.respondSuccess(
                Constants.Messages.LOGIN_SUCCESS,
                LoginResponse(token = token, email = user.email),
                HttpStatusCode.OK
            )
        } catch (e: Exception) {
            call.respondError(
                "${Constants.Messages.INTERNAL_SERVER_ERROR}: ${e.localizedMessage}",
                Unit,
                HttpStatusCode.InternalServerError
            )
        }
    }
}
