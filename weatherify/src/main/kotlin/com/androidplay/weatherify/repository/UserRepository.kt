package com.androidplay.weatherify.repository

import com.androidplay.weatherify.domain.User
import com.androidplay.core.common.Result

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
     * Update user's FCM token by email.
     * @param email The email of the user.
     * @param fcmToken The FCM token to set.
     * @return Result indicating success or failure.
     */
    suspend fun updateFcmTokenByEmail(email: String, fcmToken: String): Result<Boolean>

    /**
     * Clear user's FCM token by email.
     * @param email The email of the user.
     * @return Result indicating success or failure.
     */
    suspend fun clearFcmTokenByEmail(email: String): Result<Boolean>

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


    /**
     * Store the GCS object name for the user's profile photo.
     * @param email The email of the user.
     * @param photoObject UUID object key in the photos bucket (not a URL).
     */
    suspend fun updatePhotoObjectByEmail(email: String, photoObject: String): Result<Boolean>

    /**
     * Clear the profile photo object name for the user.
     * @param email The email of the user.
     */
    suspend fun clearPhotoObjectByEmail(email: String): Result<Boolean>

    /**
     * Delete a user by email.
     * @param email The email of the user to delete.
     * @return Result indicating success or failure.
     */
    suspend fun deleteUserByEmail(email: String): Result<Boolean>

    /**
     * Soft-delete a user: deactivate the account and subscription, wipe credentials and
     * personal/device data, and stamp [deletedAt]. The document itself is retained.
     * @param email The email of the user.
     * @param deletedAt ISO-8601 UTC deletion time; also becomes the subscription cut-off.
     */
    suspend fun markUserDeleted(email: String, deletedAt: String): Result<Boolean>
}
