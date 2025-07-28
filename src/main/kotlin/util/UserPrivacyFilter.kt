package util

import bose.ankush.data.model.User
import kotlinx.serialization.Serializable

/**
 * Utility class for filtering sensitive user information based on privacy settings
 */
object UserPrivacyFilter {

    /**
     * Filter sensitive information from a user based on privacy level
     * @param user The user to filter
     * @param privacyLevel The privacy level to apply
     * @return A filtered user with sensitive information masked or removed based on privacy level
     */
    fun filterUser(user: User, privacyLevel: PrivacyLevel): FilteredUser {
        return when (privacyLevel) {
            PrivacyLevel.HIGH -> FilteredUser(
                email = maskEmail(user.email),
                isActive = user.isActive,
                role = user.role,
                createdAt = user.createdAt,
                deviceModel = null,
                operatingSystem = null,
                osVersion = null,
                appVersion = null,
                ipAddress = null,
                registrationSource = null
            )

            PrivacyLevel.MEDIUM -> FilteredUser(
                email = maskEmail(user.email),
                isActive = user.isActive,
                role = user.role,
                createdAt = user.createdAt,
                deviceModel = user.deviceModel,
                operatingSystem = user.operatingSystem,
                osVersion = null,
                appVersion = user.appVersion,
                ipAddress = null,
                registrationSource = user.registrationSource
            )

            PrivacyLevel.LOW -> FilteredUser(
                email = user.email,
                isActive = user.isActive,
                role = user.role,
                createdAt = user.createdAt,
                deviceModel = user.deviceModel,
                operatingSystem = user.operatingSystem,
                osVersion = user.osVersion,
                appVersion = user.appVersion,
                ipAddress = maskIpAddress(user.ipAddress),
                registrationSource = user.registrationSource
            )

            PrivacyLevel.NONE -> FilteredUser(
                email = user.email,
                isActive = user.isActive,
                role = user.role,
                createdAt = user.createdAt,
                deviceModel = user.deviceModel,
                operatingSystem = user.operatingSystem,
                osVersion = user.osVersion,
                appVersion = user.appVersion,
                ipAddress = user.ipAddress,
                registrationSource = user.registrationSource
            )
        }
    }

    /**
     * Filter sensitive information from a list of users based on privacy level
     * @param users The list of users to filter
     * @param privacyLevel The privacy level to apply
     * @return A list of filtered users with sensitive information masked or removed based on privacy level
     */
    fun filterUsers(users: List<User>, privacyLevel: PrivacyLevel): List<FilteredUser> {
        return users.map { filterUser(it, privacyLevel) }
    }

    /**
     * Mask an email address for privacy
     * @param email The email address to mask
     * @return A masked email address (e.g., "j***@example.com")
     */
    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email

        val username = parts[0]
        val domain = parts[1]

        val maskedUsername = if (username.length > 1) {
            username.first() + "*".repeat(username.length - 1)
        } else {
            username
        }

        return "$maskedUsername@$domain"
    }

    /**
     * Mask an IP address for privacy
     * @param ipAddress The IP address to mask
     * @return A masked IP address (e.g., "192.168.x.x")
     */
    private fun maskIpAddress(ipAddress: String?): String? {
        if (ipAddress == null) return null

        val parts = ipAddress.split(".")
        if (parts.size != 4) return ipAddress

        return "${parts[0]}.${parts[1]}.x.x"
    }
}

/**
 * Privacy levels for filtering user data
 */
enum class PrivacyLevel {
    /**
     * No filtering, all data is visible
     */
    NONE,

    /**
     * Low filtering, only the most sensitive data is masked (e.g., IP address)
     */
    LOW,

    /**
     * Medium filtering, some sensitive data is masked or removed
     */
    MEDIUM,

    /**
     * High filtering, most sensitive data is masked or removed
     */
    HIGH
}

/**
 * Filtered user data with sensitive information masked or removed based on privacy level
 */
@Serializable
data class FilteredUser(
    val email: String,
    val isActive: Boolean,
    val role: bose.ankush.data.model.UserRole,
    val createdAt: String,
    val deviceModel: String?,
    val operatingSystem: String?,
    val osVersion: String?,
    val appVersion: String?,
    val ipAddress: String?,
    val registrationSource: String?
)