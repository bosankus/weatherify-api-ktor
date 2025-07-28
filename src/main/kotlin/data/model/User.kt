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
    val role: UserRole = UserRole.USER,
    val timestampOfRegistration: String? = null,
    val deviceModel: String? = null,
    val operatingSystem: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val ipAddress: String? = null,
    val registrationSource: String? = null
)

/** User roles for access control */
@Serializable
enum class UserRole {
    USER,
    MODERATOR,
    ADMIN
}

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

/** Login response with authentication token and user information */
@Serializable
data class LoginResponse(
    val token: String,
    val email: String,
    val role: UserRole = UserRole.USER,
    val isActive: Boolean = true
)

/** Token refresh request data */
@Serializable
data class TokenRefreshRequest(
    val token: String
)