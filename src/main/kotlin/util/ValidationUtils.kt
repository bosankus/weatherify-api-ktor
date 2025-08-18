package util

import bose.ankush.data.model.User
import bose.ankush.data.model.UserRole
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * Utility class for validating input data
 */
object ValidationUtils {
    private val logger = LoggerFactory.getLogger(ValidationUtils::class.java)

    // Email validation pattern
    private val EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    )

    // Password validation pattern (at least 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char)
    private val PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
    )

    /**
     * Validate email format
     * @param email The email to validate
     * @return True if the email is valid, false otherwise
     */
    fun isValidEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) {
            logger.debug("Email validation failed: email is null or blank")
            return false
        }

        val isValid = EMAIL_PATTERN.matcher(email).matches()
        if (!isValid) {
            logger.debug("Email validation failed for: $email")
        }
        return isValid
    }

    /**
     * Validate password strength
     * @param password The password to validate
     * @return True if the password is strong enough, false otherwise
     */
    fun isValidPassword(password: String?): Boolean {
        if (password.isNullOrBlank()) {
            logger.debug("Password validation failed: password is null or blank")
            return false
        }

        val isValid = PASSWORD_PATTERN.matcher(password).matches()
        if (!isValid) {
            logger.debug("Password validation failed: does not meet strength requirements")
        }
        return isValid
    }

    /**
     * Validate user data
     * @param user The user to validate
     * @return A list of validation errors, empty if the user is valid
     */
    fun validateUser(user: User?): List<String> {
        val errors = mutableListOf<String>()

        if (user == null) {
            errors.add("User cannot be null")
            return errors
        }

        if (!isValidEmail(user.email)) {
            errors.add("Invalid email format: ${user.email}")
        }

        if (user.passwordHash.isBlank()) {
            errors.add("Password hash cannot be empty")
        }

        // Role can be null; when present it's an enum and already type-safe
        // No validation needed here

        return errors
    }

    /**
     * Validate map of field updates
     * @param updates The map of field updates to validate
     * @param allowedFields The set of allowed field names
     * @return A list of validation errors, empty if the updates are valid
     */
    fun validateUpdates(updates: Map<String, Any>?, allowedFields: Set<String>): List<String> {
        val errors = mutableListOf<String>()

        if (updates == null || updates.isEmpty()) {
            errors.add("Updates cannot be null or empty")
            return errors
        }

        updates.forEach { (field, value) ->
            if (field !in allowedFields) {
                errors.add("Invalid field: $field")
            } else if (field == "email" && value is String && !isValidEmail(value)) {
                errors.add("Invalid email format: $value")
            } else if (field == "role" && value is String) {
                try {
                    UserRole.valueOf(value)
                } catch (_: IllegalArgumentException) {
                    errors.add("Invalid user role: $value")
                }
            }
        }

        return errors
    }

    /**
     * Validate pagination parameters
     * @param page The page number
     * @param pageSize The page size
     * @return A list of validation errors, empty if the parameters are valid
     */
    fun validatePagination(page: Int?, pageSize: Int?): List<String> {
        val errors = mutableListOf<String>()

        if (page != null && page < 1) {
            errors.add("Page number must be greater than or equal to 1")
        }

        if (pageSize != null && pageSize < 1) {
            errors.add("Page size must be greater than or equal to 1")
        }

        return errors
    }

    /**
     * Validate sorting parameters
     * @param sortBy The field to sort by
     * @param sortOrder The sort order (1 for ascending, -1 for descending)
     * @param allowedFields The set of allowed field names for sorting
     * @return A list of validation errors, empty if the parameters are valid
     */
    fun validateSorting(
        sortBy: String?,
        sortOrder: Int?,
        allowedFields: Set<String>
    ): List<String> {
        val errors = mutableListOf<String>()

        if (sortBy != null && sortBy !in allowedFields) {
            errors.add("Invalid sort field: $sortBy")
        }

        if (sortOrder != null && sortOrder != 1 && sortOrder != -1) {
            errors.add("Sort order must be 1 (ascending) or -1 (descending)")
        }

        return errors
    }

    /**
     * Validate filter parameters
     * @param filter The filter map
     * @param allowedFields The set of allowed field names for filtering
     * @return A list of validation errors, empty if the parameters are valid
     */
    fun validateFilter(filter: Map<String, Any>?, allowedFields: Set<String>): List<String> {
        val errors = mutableListOf<String>()

        if (filter == null) {
            return errors
        }

        filter.forEach { (field, _) ->
            if (field !in allowedFields) {
                errors.add("Invalid filter field: $field")
            }
        }

        return errors
    }
}