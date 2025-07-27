package bose.ankush.data.model

import kotlinx.serialization.Serializable
import java.time.Instant

/** User model with authentication and status information */
@Serializable
data class User(
    val email: String,
    val passwordHash: String,
    val createdAt: String = Instant.now().toString(),
    val isActive: Boolean = true,
    val timestampOfRegistration: String? = null,
    val deviceModel: String? = null,
    val operatingSystem: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val ipAddress: String? = null,
    val registrationSource: String? = null
)

/** User registration request data */
@Serializable
data class UserRegistrationRequest(
    val email: String,
    val password: String,
    val timestampOfRegistration: String? = null,
    val deviceModel: String? = null,
    val operatingSystem: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val ipAddress: String? = null,
    val registrationSource: String? = null
)

/** User login request data */
@Serializable
data class UserLoginRequest(
    val email: String,
    val password: String
)

/** Login response with authentication token */
@Serializable
data class LoginResponse(
    val token: String,
    val email: String
)