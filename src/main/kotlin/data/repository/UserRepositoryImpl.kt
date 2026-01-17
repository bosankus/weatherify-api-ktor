package data.repository

import bose.ankush.data.model.User
import bose.ankush.data.model.UserRole
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import util.Constants
import util.ValidationUtils

/**
 * Implementation of UserRepository that uses MongoDB for data storage.
 */
class UserRepositoryImpl(private val databaseModule: DatabaseModule) : UserRepository {
    private val logger = LoggerFactory.getLogger(UserRepositoryImpl::class.java)

    /**
     * Find a user by email.
     * @param email The email of the user to find.
     * @return Result containing the user if found, or an error if not found or an exception occurred.
     */
    override suspend fun findUserByEmail(email: String): Result<User?> {
        logger.debug("Finding user by email: $email")
        return try {
            val query = databaseModule.createFilter(Constants.Database.EMAIL_FIELD, email)
            val docCollection =
                databaseModule.getUsersCollection().withDocumentClass(Document::class.java)
            val doc = docCollection.find(query).firstOrNull()
            if (doc == null) {
                logger.debug("User not found: $email")
                return Result.success(null)
            }

            val rawId = doc["_id"]
            val idStr = when (rawId) {
                is ObjectId -> rawId.toHexString()
                is String -> rawId
                else -> rawId?.toString() ?: ObjectId().toHexString()
            }

            val roleValue = doc.get("role")
            val role = when (roleValue) {
                is String -> try {
                    UserRole.valueOf(roleValue)
                } catch (_: Exception) {
                    null
                }

                else -> null
            }

            val user = User(
                id = ObjectId(idStr),
                email = doc.getString("email"),
                passwordHash = doc.getString("passwordHash"),
                createdAt = doc.getString("createdAt") ?: java.time.Instant.now().toString(),
                isActive = (doc.get("isActive") as? Boolean) ?: false,
                role = role,
                timestampOfRegistration = doc.getString("timestampOfRegistration"),
                deviceModel = doc.getString("deviceModel"),
                operatingSystem = doc.getString("operatingSystem"),
                osVersion = doc.getString("osVersion"),
                appVersion = doc.getString("appVersion"),
                ipAddress = doc.getString("ipAddress"),
                registrationSource = doc.getString("registrationSource"),
                isPremium = (doc.get("isPremium") as? Boolean) ?: false,
                fcmToken = doc.getString("fcmToken")
            )

            logger.debug("User found: $email")
            Result.success(user)
        } catch (e: Exception) {
            logger.error("Failed to find user by email: $email", e)
            Result.error("Failed to find user by email: ${e.message}", e)
        }
    }

    /**
     * Create a new user.
     * @param user The user to create.
     * @return Result indicating success or failure.
     */
    override suspend fun createUser(user: User): Result<Boolean> {
        logger.debug("Creating new user: ${user.email}")

        // Validate user data
        val validationErrors = ValidationUtils.validateUser(user)
        if (validationErrors.isNotEmpty()) {
            val errorMessage = "Invalid user data: ${validationErrors.joinToString(", ")}"
            logger.warn(errorMessage)
            return Result.error(errorMessage)
        }

        return try {
            databaseModule.getUsersCollection().insertOne(user)
            logger.info("User created successfully: ${user.email}")
            Result.success(true)
        } catch (e: Exception) {
            logger.error("Failed to create user: ${user.email}", e)
            Result.error("Failed to create user: ${e.message}", e)
        }
    }

    /**
     * Update an existing user.
     * @param user The user to update.
     * @return Result indicating success or failure.
     */
    override suspend fun updateUser(user: User): Result<Boolean> {
        logger.debug("Updating user: ${user.email}")

        // Validate user data
        val validationErrors = ValidationUtils.validateUser(user)
        if (validationErrors.isNotEmpty()) {
            val errorMessage = "Invalid user data: ${validationErrors.joinToString(", ")}"
            logger.warn(errorMessage)
            return Result.error(errorMessage)
        }

        // Check if user exists
        val existingUserResult = findUserByEmail(user.email)
        if (existingUserResult is Result.Error) {
            return existingUserResult.mapError { msg, ex ->
                Pair(
                    "Failed to check if user exists: $msg",
                    ex
                )
            }
        }

        val existingUser = (existingUserResult as Result.Success).data
        if (existingUser == null) {
            val errorMessage = "User not found: ${user.email}"
            logger.warn(errorMessage)
            return Result.error(errorMessage)
        }

        return try {
            // Build partial updates for mutable fields only. Do not replace the whole document
            // and never include immutable fields like _id or email in updates.
            val updates = mutableMapOf<String, Any>()

            if (user.passwordHash != existingUser.passwordHash) updates["passwordHash"] =
                user.passwordHash
            if (user.isActive != existingUser.isActive) updates["isActive"] = user.isActive
            if ((user.role?.name ?: "") != (existingUser.role?.name ?: "")) updates["role"] =
                user.role?.name ?: "USER"
            if (user.timestampOfRegistration != null && user.timestampOfRegistration != existingUser.timestampOfRegistration) updates["timestampOfRegistration"] =
                user.timestampOfRegistration
            if (user.deviceModel != null && user.deviceModel != existingUser.deviceModel) updates["deviceModel"] =
                user.deviceModel
            if (user.operatingSystem != null && user.operatingSystem != existingUser.operatingSystem) updates["operatingSystem"] =
                user.operatingSystem
            if (user.osVersion != null && user.osVersion != existingUser.osVersion) updates["osVersion"] =
                user.osVersion
            if (user.appVersion != null && user.appVersion != existingUser.appVersion) updates["appVersion"] =
                user.appVersion
            if (user.ipAddress != null && user.ipAddress != existingUser.ipAddress) updates["ipAddress"] =
                user.ipAddress
            if (user.registrationSource != null && user.registrationSource != existingUser.registrationSource) updates["registrationSource"] =
                user.registrationSource
            if (user.isPremium != existingUser.isPremium) updates["isPremium"] = user.isPremium
            if (user.fcmToken != null && user.fcmToken != existingUser.fcmToken) updates["fcmToken"] =
                user.fcmToken

            if (updates.isEmpty()) {
                logger.info("No changes detected for user: ${user.email}")
                return Result.success(true)
            }

            val filter = databaseModule.createFilter(Constants.Database.EMAIL_FIELD, user.email)
            val updateBson = databaseModule.createSetUpdates(updates)
            val result = databaseModule.getUsersCollection().updateOne(filter, updateBson)

            if (result.matchedCount == 0L) {
                val msg = "User not found during update: ${user.email}"
                logger.warn(msg)
                return Result.error(msg)
            }

            if (result.modifiedCount > 0) {
                logger.info("User updated successfully: ${user.email}")
            } else {
                logger.info("User update acknowledged but no values changed: ${user.email}")
            }

            Result.success(true)
        } catch (e: Exception) {
            logger.error("Failed to update user: ${user.email}", e)
            Result.error("Failed to update user: ${e.message}", e)
        }
    }

    /**
     * Delete a user by email.
     * @param email The email of the user to delete.
     * @return Result indicating success or failure.
     */
    override suspend fun deleteUserByEmail(email: String): Result<Boolean> {
        logger.debug("Deleting user by email: $email")
        return try {
            val filter = databaseModule.createFilter(Constants.Database.EMAIL_FIELD, email)
            val result = databaseModule.getUsersCollection().deleteOne(filter)
            if (result.deletedCount > 0) {
                logger.info("User deleted successfully: $email")
                Result.success(true)
            } else {
                logger.warn("User not found for deletion: $email")
                Result.error("User not found: $email")
            }
        } catch (e: Exception) {
            logger.error("Failed to delete user: $email", e)
            Result.error("Failed to delete user: ${e.message}", e)
        }
    }

    /**
     * Get all users with optional filtering, sorting, and pagination.
     * @param filter Optional filter criteria (e.g., by email, role, isActive)
     * @param sortBy Optional field to sort by
     * @param sortOrder Optional sort order (ascending or descending)
     * @param page Optional page number for pagination
     * @param pageSize Optional page size for pagination
     * @return Result containing a list of users and total count
     */
    override suspend fun getAllUsers(
        filter: Map<String, Any>?,
        sortBy: String?,
        sortOrder: Int?,
        page: Int?,
        pageSize: Int?
    ): Result<Pair<List<User>, Long>> {
        logger.debug(
            "Getting all users with filter: {}, sortBy: {}, sortOrder: {}, page: {}, pageSize: {}",
            filter,
            sortBy,
            sortOrder,
            page,
            pageSize
        )

        // Validate pagination parameters
        val paginationErrors = ValidationUtils.validatePagination(page, pageSize)
        if (paginationErrors.isNotEmpty()) {
            val errorMessage =
                "Invalid pagination parameters: ${paginationErrors.joinToString(", ")}"
            logger.warn(errorMessage)
            return Result.error(errorMessage)
        }

        // Validate sorting parameters
        val allowedSortFields = setOf(
            "email",
            "createdAt",
            "isActive",
            "role",
            "deviceModel",
            "operatingSystem",
            "registrationSource"
        )
        val sortingErrors = ValidationUtils.validateSorting(sortBy, sortOrder, allowedSortFields)
        if (sortingErrors.isNotEmpty()) {
            val errorMessage = "Invalid sorting parameters: ${sortingErrors.joinToString(", ")}"
            logger.warn(errorMessage)
            return Result.error(errorMessage)
        }

        // Validate filter parameters
        val allowedFilterFields = setOf(
            "email",
            "isActive",
            "role",
            "deviceModel",
            "operatingSystem",
            "registrationSource"
        )
        val filterErrors = ValidationUtils.validateFilter(filter, allowedFilterFields)
        if (filterErrors.isNotEmpty()) {
            val errorMessage = "Invalid filter parameters: ${filterErrors.joinToString(", ")}"
            logger.warn(errorMessage)
            return Result.error(errorMessage)
        }

        return try {
            // Build filter
            val filterList = mutableListOf<Bson>()

            filter?.forEach { (key, value) ->
                when (key) {
                    "email" -> filterList.add(
                        Filters.regex(
                            Constants.Database.EMAIL_FIELD,
                            value.toString(),
                            "i"
                        )
                    )

                    "isActive" -> filterList.add(Filters.eq("isActive", value as Boolean))
                    "role" -> {
                        val role = when (value.toString().uppercase()) {
                            "ADMIN" -> UserRole.ADMIN
                            "MODERATOR" -> UserRole.MODERATOR
                            "USER" -> UserRole.USER
                            else -> { /*Nothing to do. Invalid or null role value*/
                            }
                        }
                        filterList.add(Filters.eq("role", role))
                    }

                    "deviceModel" -> filterList.add(
                        Filters.regex(
                            "deviceModel",
                            value.toString(),
                            "i"
                        )
                    )

                    "operatingSystem" -> filterList.add(
                        Filters.regex(
                            "operatingSystem",
                            value.toString(),
                            "i"
                        )
                    )

                    "registrationSource" -> filterList.add(
                        Filters.regex(
                            "registrationSource",
                            value.toString(),
                            "i"
                        )
                    )
                }
            }

            val combinedFilter = if (filterList.isEmpty()) Document() else Filters.and(filterList)
            logger.debug("Combined filter: {}", combinedFilter)

            // Count total users matching filter
            val totalCount = databaseModule.getUsersCollection().countDocuments(combinedFilter)
            logger.debug("Total users matching filter: $totalCount")

            // Build sort
            val sort = when {
                sortBy != null -> {
                    val direction = if (sortOrder != null && sortOrder < 0) -1 else 1
                    when (direction) {
                        -1 -> Sorts.descending(sortBy)
                        else -> Sorts.ascending(sortBy)
                    }
                }

                else -> Sorts.ascending(Constants.Database.EMAIL_FIELD)
            }
            logger.debug("Sort: {}", sort)

            // Apply pagination
            val skip = if (page != null && pageSize != null) (page - 1) * pageSize else 0
            val limit = pageSize ?: Int.MAX_VALUE
            logger.debug("Pagination: skip=$skip, limit=$limit")

            // Execute query with aggregation pipeline
            val pipeline = mutableListOf<Bson>()
            pipeline.add(Aggregates.match(combinedFilter))
            pipeline.add(Aggregates.sort(sort))
            // Exclude _id to avoid codec issues when it's an ObjectId in existing documents
            pipeline.add(Aggregates.project(Projections.excludeId()))
            pipeline.add(Aggregates.skip(skip))
            pipeline.add(Aggregates.limit(limit))

            val users = databaseModule.getUsersCollection().aggregate(pipeline).toList()
            logger.info("Retrieved ${users.size} users out of $totalCount total")

            Result.success(Pair(users, totalCount))
        } catch (e: Exception) {
            logger.error("Failed to get users", e)
            Result.error("Failed to get users: ${e.message}", e)
        }
    }

    override suspend fun updateFcmTokenByEmail(email: String, fcmToken: String): Result<Boolean> {
        logger.debug("Updating FCM token for user email: $email")
        if (email.isBlank() || fcmToken.isBlank()) {
            val msg = "Invalid input: email and fcmToken are required"
            logger.warn(msg)
            return Result.error(msg)
        }
        return try {
            val collection = databaseModule.getUsersCollection()
            val filter = databaseModule.createFilter(Constants.Database.EMAIL_FIELD, email)
            val update = databaseModule.createSetUpdate("fcmToken", fcmToken)
            val result = collection.updateOne(filter, update)
            when {
                result.matchedCount == 0L -> {
                    val msg = "User not found for email: $email"
                    logger.warn(msg)
                    Result.error(msg)
                }

                else -> {
                    if (result.modifiedCount > 0) {
                        logger.info("FCM token updated for user email: $email")
                    } else {
                        logger.info("FCM token value unchanged for user email: $email")
                    }
                    Result.success(true)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to update FCM token for user email: $email", e)
            Result.error("Failed to update FCM token: ${e.message}", e)
        }
    }

    override suspend fun clearFcmTokenByEmail(email: String): Result<Boolean> {
        logger.debug("Clearing FCM token for user email: $email")
        if (email.isBlank()) {
            val msg = "Invalid input: email is required"
            logger.warn(msg)
            return Result.error(msg)
        }
        return try {
            val collection = databaseModule.getUsersCollection()
            val filter = databaseModule.createFilter(Constants.Database.EMAIL_FIELD, email)
            val update = databaseModule.createUnsetUpdate("fcmToken")
            val result = collection.updateOne(filter, update)
            when {
                result.matchedCount == 0L -> {
                    val msg = "User not found for email: $email"
                    logger.warn(msg)
                    Result.error(msg)
                }

                else -> {
                    if (result.modifiedCount > 0) {
                        logger.info("FCM token cleared for user email: $email")
                    } else {
                        logger.info("FCM token already empty for user email: $email")
                    }
                    Result.success(true)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to clear FCM token for user email: $email", e)
            Result.error("Failed to clear FCM token: ${e.message}", e)
        }
    }
}
