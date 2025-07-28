package domain.repository

import bose.ankush.data.model.User
import domain.model.Result

/**
 * Repository interface for User-related operations.
 * This interface defines the contract for accessing and manipulating user data.
 */
interface UserRepository {
    /**
     * Find a user by email.
     * @param email The email of the user to find.
     * @return Result containing the user if found, or an error if not found or an exception occurred.
     */
    suspend fun findUserByEmail(email: String): Result<User?>

    /**
     * Create a new user.
     * @param user The user to create.
     * @return Result indicating success or failure.
     */
    suspend fun createUser(user: User): Result<Boolean>

    /**
     * Update an existing user.
     * @param user The user to update.
     * @return Result indicating success or failure.
     */
    suspend fun updateUser(user: User): Result<Boolean>

    /**
     * Get all users with optional filtering, sorting, and pagination.
     * @param filter Optional filter criteria (e.g., by email, role, isActive)
     * @param sortBy Optional field to sort by
     * @param sortOrder Optional sort order (ascending or descending)
     * @param page Optional page number for pagination
     * @param pageSize Optional page size for pagination
     * @return Result containing a list of users and total count
     */
    suspend fun getAllUsers(
        filter: Map<String, Any>? = null,
        sortBy: String? = null,
        sortOrder: Int? = null,
        page: Int? = null,
        pageSize: Int? = null
    ): Result<Pair<List<User>, Long>>
}