package data.repository

import bose.ankush.data.model.User
import bose.ankush.data.model.UserRole
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.bson.conversions.Bson
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
            val query = databaseModule.createQuery(Constants.Database.EMAIL_FIELD, email)
            val user = databaseModule.getUsersCollection().find(query).firstOrNull()
            if (user != null) {
                logger.debug("User found: $email")
            } else {
                logger.debug("User not found: $email")
            }
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
            val query = databaseModule.createQuery(Constants.Database.EMAIL_FIELD, user.email)
            val result = databaseModule.getUsersCollection().replaceOne(
                filter = query,
                replacement = user,
                options = ReplaceOptions().upsert(false)
            )
            val success = result.wasAcknowledged()
            if (success) {
                logger.info("User updated successfully: ${user.email}")
            } else {
                logger.warn("User update not acknowledged: ${user.email}")
            }
            Result.success(success)
        } catch (e: Exception) {
            logger.error("Failed to update user: ${user.email}", e)
            Result.error("Failed to update user: ${e.message}", e)
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
}