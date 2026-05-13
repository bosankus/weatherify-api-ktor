package bose.ankush.util

import at.favre.lib.crypto.bcrypt.BCrypt
import org.slf4j.LoggerFactory

/** Utility for password hashing, verification and validation */
object PasswordUtil {
    private val logger = LoggerFactory.getLogger(PasswordUtil::class.java)

    private const val COST_FACTOR = 12
    private const val MIN_PASSWORD_LENGTH = 8
    private const val SPECIAL_CHARS = "!@#$%^&*()_-+=<>?/[]{}|"

    // Email validation regex — TLD unbounded to support long TLDs (e.g. .photography)
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun hashPassword(password: String): String {
        try {
            return BCrypt.withDefaults().hashToString(COST_FACTOR, password.toCharArray())
        } catch (e: Exception) {
            logger.error("Error hashing password: ${e.message}", e)
            throw IllegalArgumentException("Failed to hash password: ${e.message}", e)
        }
    }

    fun verifyPassword(password: String, hash: String): Boolean {
        try {
            val result = BCrypt.verifyer().verify(password.toCharArray(), hash)
            return result.verified
        } catch (e: Exception) {
            logger.error("Error verifying password: ${e.message}", e)
            throw IllegalArgumentException("Failed to verify password: ${e.message}", e)
        }
    }

    fun validatePasswordStrength(password: String): Boolean {
        if (password.length < MIN_PASSWORD_LENGTH) return false
        if (!password.any { it.isDigit() }) return false
        if (!password.any { it.isLowerCase() }) return false
        if (!password.any { it.isUpperCase() }) return false
        if (!password.any { SPECIAL_CHARS.contains(it) }) return false
        return true
    }

    fun validateEmailFormat(email: String): Boolean {
        return EMAIL_REGEX.matches(email)
    }
}