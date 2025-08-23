package bose.ankush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.time.Instant

/** User model with authentication and status information */
@Serializable
data class User(
    @SerialName("_id")
    val id: String = ObjectId().toHexString(),
    val email: String,
    val passwordHash: String,
    val createdAt: String = Instant.now().toString(),
    val isActive: Boolean = false,
    val role: UserRole? = null,
    val timestampOfRegistration: String? = null,
    val deviceModel: String? = null,
    val operatingSystem: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val ipAddress: String? = null,
    val registrationSource: String? = null,
    val isPremium: Boolean = false,
    val fcmToken: String? = null
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
    val registrationSource: String? = null,
    val firebaseToken: String? = null,
    val role: UserRole? = UserRole.USER,
    val isActive: Boolean = true,
    val isPremium: Boolean = false
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
    val role: UserRole?,
    val isActive: Boolean,
    val isPremium: Boolean
)

/** Token refresh request data */
@Serializable
data class TokenRefreshRequest(
    val token: String
)
