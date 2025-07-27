package data.repository

import bose.ankush.data.model.User
import com.mongodb.client.model.ReplaceOptions
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import util.Constants

/**
 * Implementation of UserRepository that uses MongoDB for data storage.
 */
class UserRepositoryImpl(private val databaseModule: DatabaseModule) : UserRepository {

    /**
     * Find a user by email.
     * @param email The email of the user to find.
     * @return Result containing the user if found, or an error if not found or an exception occurred.
     */
    override suspend fun findUserByEmail(email: String): Result<User?> {
        return try {
            val query = databaseModule.createQuery(Constants.Database.EMAIL_FIELD, email)
            val user = databaseModule.getUsersCollection().find(query).firstOrNull()
            Result.success(user)
        } catch (e: Exception) {
            Result.error("Failed to find user by email: ${e.message}", e)
        }
    }

    /**
     * Create a new user.
     * @param user The user to create.
     * @return Result indicating success or failure.
     */
    override suspend fun createUser(user: User): Result<Boolean> {
        return try {
            databaseModule.getUsersCollection().insertOne(user)
            Result.success(true)
        } catch (e: Exception) {
            Result.error("Failed to create user: ${e.message}", e)
        }
    }

    /**
     * Update an existing user.
     * @param user The user to update.
     * @return Result indicating success or failure.
     */
    override suspend fun updateUser(user: User): Result<Boolean> {
        return try {
            val query = databaseModule.createQuery(Constants.Database.EMAIL_FIELD, user.email)
            val result = databaseModule.getUsersCollection().replaceOne(
                filter = query,
                replacement = user,
                options = ReplaceOptions().upsert(false)
            )
            Result.success(result.wasAcknowledged())
        } catch (e: Exception) {
            Result.error("Failed to update user: ${e.message}", e)
        }
    }
}