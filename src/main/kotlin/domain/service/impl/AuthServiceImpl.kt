package domain.service.impl

import bose.ankush.util.PasswordUtil
import config.JwtConfig
import domain.model.Result
import domain.repository.UserRepository
import domain.service.AuthService
import util.Constants
import java.util.regex.Pattern

/**
 * Implementation of AuthService that handles authentication business logic.
 */
class AuthServiceImpl(private val userRepository: UserRepository) : AuthService {

    // Email validation pattern
    private val emailPattern = Pattern.compile(
        "[a-zA-Z0-9+._%\\-]{1,256}" +
                "@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                ")+"
    )

    /**
     * Login a user.
     * @param email The email of the user to login.
     * @param password The password of the user to login.
     * @return Result containing the JWT token if login is successful, or an error if login failed.
     */
    override suspend fun loginUser(email: String, password: String): Result<String> {
        // Find user by email
        val userResult = userRepository.findUserByEmail(email)
        val user = userResult.getOrNull()

        if (user == null) {
            return Result.error(Constants.Messages.USER_NOT_REGISTERED)
        }

        // Verify password
        if (!PasswordUtil.verifyPassword(password, user.passwordHash)) {
            return Result.error(Constants.Messages.INVALID_CREDENTIALS)
        }

        // Check if user is active
        if (!user.isActive) {
            return Result.error(Constants.Messages.ACCOUNT_INACTIVE)
        }

        // Generate JWT token
        val token = JwtConfig.generateToken(user.email, user.role)
        return Result.success(token)
    }

    /**
     * Validate a JWT token.
     * @param token The JWT token to validate.
     * @return Result containing the user email if the token is valid, or an error if the token is invalid.
     */
    override fun validateToken(token: String): Result<String> {
        return try {
            val jwt = JwtConfig.verifier.verify(token)
            val email = jwt.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()

            if (email.isNullOrEmpty()) {
                Result.error("Invalid token: missing email claim")
            } else {
                Result.success(email)
            }
        } catch (e: Exception) {
            Result.error("Invalid token: ${e.message}", e)
        }
    }

    /**
     * Check if an email is valid.
     * @param email The email to validate.
     * @return True if the email is valid, false otherwise.
     */
    override fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() && emailPattern.matcher(email).matches()
    }

    /**
     * Check if a password is strong enough.
     * @param password The password to validate.
     * @return True if the password is strong enough, false otherwise.
     */
    override fun isStrongPassword(password: String): Boolean {
        // Password must be at least 8 characters long and contain at least one digit, one lowercase letter,
        // one uppercase letter, and one special character
        val hasMinLength = password.length >= 8
        val hasDigit = password.any { it.isDigit() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }

        return hasMinLength && hasDigit && hasLowerCase && hasUpperCase && hasSpecialChar
    }
}