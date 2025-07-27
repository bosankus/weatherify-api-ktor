package domain.service

import bose.ankush.data.model.User
import domain.model.Result

/**
 * Service interface for authentication-related operations.
 * This interface defines the contract for authentication business logic.
 */
interface AuthService {
    /**
     * Register a new user.
     * @param email The email of the user to register.
     * @param password The password of the user to register.
     * @return Result containing the created user if successful, or an error if registration failed.
     */
    suspend fun registerUser(email: String, password: String): Result<User>

    /**
     * Login a user.
     * @param email The email of the user to login.
     * @param password The password of the user to login.
     * @return Result containing the JWT token if login is successful, or an error if login failed.
     */
    suspend fun loginUser(email: String, password: String): Result<String>

    /**
     * Validate a JWT token.
     * @param token The JWT token to validate.
     * @return Result containing the user email if the token is valid, or an error if the token is invalid.
     */
    fun validateToken(token: String): Result<String>

    /**
     * Check if an email is valid.
     * @param email The email to validate.
     * @return True if the email is valid, false otherwise.
     */
    fun isValidEmail(email: String): Boolean

    /**
     * Check if a password is strong enough.
     * @param password The password to validate.
     * @return True if the password is strong enough, false otherwise.
     */
    fun isStrongPassword(password: String): Boolean
}