package bose.ankush.util

import at.favre.lib.crypto.bcrypt.BCrypt

/** Utility for password hashing, verification and validation */
object PasswordUtil {
    // BCrypt cost factor (higher = more secure but slower)
    private const val COST_FACTOR = 12

    // Password validation constants
    private const val MIN_PASSWORD_LENGTH = 8
    private const val SPECIAL_CHARS = "!@#$%^&*()_-+=<>?/[]{}|"

    // Email validation regex
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")

    /** Hash a password using BCrypt */
    fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(COST_FACTOR, password.toCharArray())
    }

    /** Verify a password against a hash */
    fun verifyPassword(password: String, hash: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hash)
        return result.verified
    }

    /** Validate password strength */
    fun validatePasswordStrength(password: String): Boolean {
        // Password must be at least MIN_PASSWORD_LENGTH characters long
        if (password.length < MIN_PASSWORD_LENGTH) return false

        // Password must contain at least one digit
        if (!password.any { it.isDigit() }) return false

        // Password must contain at least one lowercase letter
        if (!password.any { it.isLowerCase() }) return false

        // Password must contain at least one uppercase letter
        if (!password.any { it.isUpperCase() }) return false

        // Password must contain at least one special character
        if (!password.any { SPECIAL_CHARS.contains(it) }) return false

        return true
    }

    /** Validate email format */
    fun validateEmailFormat(email: String): Boolean {
        return EMAIL_REGEX.matches(email)
    }
}