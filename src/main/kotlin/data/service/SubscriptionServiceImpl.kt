package data.service

import bose.ankush.data.model.*
import config.Environment
import domain.model.Result
import domain.repository.PaymentRepository
import domain.repository.UserRepository
import domain.service.SubscriptionService
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory

class SubscriptionServiceImpl(
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository,
    private val emailService: domain.service.EmailService
) : SubscriptionService {
    private val logger = LoggerFactory.getLogger(SubscriptionServiceImpl::class.java)

    override suspend fun getSubscriptionStatus(userEmail: String): Result<SubscriptionResponse> {
        logger.debug("Getting subscription status for user: $userEmail")
        return try {
            val userResult = userRepository.findUserByEmail(userEmail)
            if (userResult is Result.Error) {
                return Result.error("Failed to find user: ${userResult.message}")
            }

            val user = (userResult as Result.Success).data ?: return Result.error("User not found")
            val activeSubscription =
                findActiveOrGracePeriodSubscription(user.subscriptions)
                    ?: return Result.error("No active subscription found")

            Result.success(mapToSubscriptionResponse(activeSubscription))
        } catch (e: Exception) {
            logger.error("Failed to get subscription status for user: $userEmail", e)
            Result.error("Failed to get subscription status: ${e.message}", e)
        }
    }

    override suspend fun getSubscriptionHistory(
        userEmail: String
    ): Result<SubscriptionHistoryResponse> {
        logger.debug("Getting subscription history for user: $userEmail")
        return try {
            val userResult = userRepository.findUserByEmail(userEmail)
            if (userResult is Result.Error) {
                return Result.error("Failed to find user: ${userResult.message}")
            }

            val user = (userResult as Result.Success).data ?: return Result.error("User not found")
            val subscriptionResponses = user.subscriptions.map { mapToSubscriptionResponse(it) }
            val response =
                SubscriptionHistoryResponse(
                    subscriptions = subscriptionResponses,
                    totalCount = subscriptionResponses.size
                )
            Result.success(response)
        } catch (e: Exception) {
            logger.error("Failed to get subscription history for user: $userEmail", e)
            Result.error("Failed to get subscription history: ${e.message}", e)
        }
    }

    override suspend fun cancelSubscription(userEmail: String): Result<SubscriptionResponse> {
        logger.info("=== Starting subscription cancellation for user: $userEmail ===")
        return try {
            // Step 1: Find user
            logger.debug("Step 1: Fetching user from repository")
            val userResult = userRepository.findUserByEmail(userEmail)
            if (userResult is Result.Error) {
                logger.error("Failed to find user $userEmail: ${userResult.message}")
                return Result.error("Failed to find user: ${userResult.message}")
            }

            val user = (userResult as Result.Success).data
            if (user == null) {
                logger.error("User not found: $userEmail")
                return Result.error("User not found")
            }

            // Step 2: Log user subscription data
            logger.info("Step 2: User found - email=$userEmail, isPremium=${user.isPremium}, subscriptionCount=${user.subscriptions.size}")

            // Step 3: Validate and log subscription data
            logger.info("Step 3: Validating subscription data")
            if (user.subscriptions.isEmpty()) {
                logger.warn("User $userEmail has no subscriptions in subscriptions array")
                return Result.error("No active subscription to cancel")
            }

            // Log each subscription with detailed information
            user.subscriptions.forEachIndexed { index, sub ->
                logger.info("  Subscription[$index]: status=${sub.status}, service=${sub.service}, startDate=${sub.startDate}, endDate=${sub.endDate}, createdAt=${sub.createdAt}, cancelledAt=${sub.cancelledAt}, gracePeriodEnd=${sub.gracePeriodEnd}")

                // Validate subscription status
                try {
                    val validStatuses = listOf(
                        SubscriptionStatus.ACTIVE,
                        SubscriptionStatus.EXPIRED,
                        SubscriptionStatus.CANCELLED,
                        SubscriptionStatus.GRACE_PERIOD
                    )
                    if (!validStatuses.contains(sub.status)) {
                        logger.error("  WARNING: Subscription[$index] has invalid status: ${sub.status}")
                    }
                } catch (e: Exception) {
                    logger.error("  ERROR: Failed to validate subscription[$index] status", e)
                }

                // Validate date fields
                try {
                    Instant.parse(sub.startDate)
                    Instant.parse(sub.endDate)
                    logger.debug("  Subscription[$index] date fields are valid ISO 8601 timestamps")
                } catch (e: Exception) {
                    logger.error(
                        "  ERROR: Subscription[$index] has invalid date format - startDate=${sub.startDate}, endDate=${sub.endDate}",
                        e
                    )
                }
            }

            // Step 4: Search for cancellable subscription
            logger.info("Step 4: Searching for cancellable subscription (ACTIVE or GRACE_PERIOD)")
            val activeSubscriptions = user.subscriptions.filter {
                it.status == SubscriptionStatus.ACTIVE ||
                    it.status == SubscriptionStatus.GRACE_PERIOD
            }

            logger.info("Found ${activeSubscriptions.size} cancellable subscription(s)")
            activeSubscriptions.forEachIndexed { index, sub ->
                logger.info("  Cancellable[$index]: status=${sub.status}, service=${sub.service}, endDate=${sub.endDate}")
            }

            val activeSubscription = activeSubscriptions.firstOrNull()

            if (activeSubscription == null) {
                logger.warn("No active or grace period subscription found for user $userEmail")
                logger.warn("Available subscription statuses: ${user.subscriptions.map { it.status }}")
                return Result.error("No active subscription to cancel")
            }

            logger.info("Step 5: Found subscription to cancel - status=${activeSubscription.status}, service=${activeSubscription.service}")

            // Step 6: Update subscription status
            logger.info("Step 6: Updating subscription status to CANCELLED")
            val now = Instant.now().toString()
            val updatedSubscriptions =
                user.subscriptions.map {
                    if (it.status == SubscriptionStatus.ACTIVE ||
                        it.status == SubscriptionStatus.GRACE_PERIOD
                    ) {
                        logger.debug("  Cancelling subscription with status=${it.status}")
                        it.copy(status = SubscriptionStatus.CANCELLED, cancelledAt = now)
                    } else {
                        it
                    }
                }

            // Step 7: Update user in database
            logger.info("Step 7: Updating user in database - setting isPremium=false")
            val updatedUser = user.copy(subscriptions = updatedSubscriptions, isPremium = false)
            val updateResult = userRepository.updateUser(updatedUser)
            if (updateResult is Result.Error) {
                logger.error("Failed to update user in database: ${updateResult.message}")
                return Result.error("Failed to update user: ${updateResult.message}")
            }

            logger.info("User updated successfully in database")

            val cancelledSubscription = updatedSubscriptions.find { it.cancelledAt == now }!!
            logger.info("Step 8: Subscription cancelled successfully - status=${cancelledSubscription.status}, cancelledAt=${cancelledSubscription.cancelledAt}")

            // Step 9: Send cancellation email
            logger.info("Step 9: Sending cancellation email to user")
            try {
                emailService.sendCancellationEmail(userEmail, cancelledSubscription.endDate)
                logger.info("Cancellation email sent successfully")
            } catch (e: Exception) {
                logger.warn("Failed to send cancellation email to $userEmail, but subscription was cancelled", e)
                // Don't fail the cancellation if email fails
            }

            logger.info("=== Subscription cancellation completed successfully for user: $userEmail ===")
            Result.success(mapToSubscriptionResponse(cancelledSubscription))
        } catch (e: Exception) {
            logger.error("=== EXCEPTION during subscription cancellation for user: $userEmail ===", e)
            logger.error("Exception type: ${e.javaClass.name}, message: ${e.message}")
            Result.error("Failed to cancel subscription: ${e.message}", e)
        }
    }

    override suspend fun getAllSubscriptions(
        page: Int,
        pageSize: Int,
        statusFilter: SubscriptionStatus?
    ): Result<AdminSubscriptionListResponse> {
        logger.debug(
            "Getting all subscriptions with page: $page, pageSize: $pageSize, filter: $statusFilter"
        )
        return try {
            // Fetch all users without pagination to properly filter subscriptions
            val usersResult = userRepository.getAllUsers()

            if (usersResult is Result.Error) {
                return Result.error("Failed to get users: ${usersResult.message}")
            }

            val (users, _) = (usersResult as Result.Success).data

            // Flatten all subscriptions from all users and filter by status
            val allSubscriptionViews = mutableListOf<AdminSubscriptionView>()

            for (user in users) {
                for (subscription in user.subscriptions) {
                    // Apply status filter
                    if (statusFilter == null || subscription.status == statusFilter) {
                        val payment =
                            subscription.sourcePaymentId?.let {
                                val paymentResult =
                                    paymentRepository.getPaymentByTransactionId(it)
                                if (paymentResult is Result.Success) paymentResult.data
                                else null
                            }

                        allSubscriptionViews.add(
                            AdminSubscriptionView(
                                userEmail = user.email,
                                userId = user.id.toHexString(),
                                service = subscription.service,
                                startDate = subscription.startDate,
                                endDate = subscription.endDate,
                                status = subscription.status,
                                daysRemaining =
                                    calculateDaysRemaining(subscription.endDate),
                                paymentId = payment?.paymentId,
                                amount = payment?.amount,
                                currency = payment?.currency,
                                createdAt = subscription.createdAt
                            )
                        )
                    }
                }
            }

            // Apply pagination to filtered results
            val totalCount = allSubscriptionViews.size.toLong()
            val startIndex = (page - 1) * pageSize
            val endIndex = minOf(startIndex + pageSize, allSubscriptionViews.size)

            val paginatedViews = if (startIndex < allSubscriptionViews.size) {
                allSubscriptionViews.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            val response =
                AdminSubscriptionListResponse(
                    subscriptions = paginatedViews,
                    totalCount = totalCount,
                    page = page,
                    pageSize = pageSize
                )
            Result.success(response)
        } catch (e: Exception) {
            logger.error("Failed to get all subscriptions", e)
            Result.error("Failed to get subscriptions: ${e.message}", e)
        }
    }

    override suspend fun getSubscriptionAnalytics(): Result<SubscriptionAnalytics> {
        logger.debug("Getting subscription analytics")
        return try {
            val usersResult = userRepository.getAllUsers()
            if (usersResult is Result.Error) {
                return Result.error("Failed to get users: ${usersResult.message}")
            }

            val (users, _) = (usersResult as Result.Success).data
            val allSubscriptions =
                users.flatMap { user -> user.subscriptions.map { sub -> user.email to sub } }

            val totalActive =
                allSubscriptions.count { it.second.status == SubscriptionStatus.ACTIVE }
            val totalExpired =
                allSubscriptions.count { it.second.status == SubscriptionStatus.EXPIRED }
            val totalCancelled =
                allSubscriptions.count { it.second.status == SubscriptionStatus.CANCELLED }

            // Get total revenue (already in rupees from repository)
            val revenueResult = paymentRepository.getTotalRevenue()
            val totalRevenue = if (revenueResult is Result.Success) revenueResult.data else 0.0

            val avgDays =
                allSubscriptions
                    .mapNotNull { (_, sub) ->
                        try {
                            val start = Instant.parse(sub.startDate)
                            val end = Instant.parse(sub.endDate)
                            Duration.between(start, end).toDays().toDouble()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    .average()
                    .takeIf { !it.isNaN() }
                    ?: 0.0

            val recentSubs =
                allSubscriptions.sortedByDescending { it.second.createdAt }.take(10).map { (email, sub) ->
                    val paymentResult =
                        sub.sourcePaymentId?.let {
                            paymentRepository.getPaymentByTransactionId(it)
                        }
                    val payment =
                        if (paymentResult is Result.Success) paymentResult.data else null

                    RecentSubscription(
                        userEmail = email,
                        service = sub.service,
                        startDate = sub.startDate,
                        amount = payment?.amount,
                        status = sub.status
                    )
                }

            val analytics =
                SubscriptionAnalytics(
                    totalActive = totalActive.toLong(),
                    totalExpired = totalExpired.toLong(),
                    totalCancelled = totalCancelled.toLong(),
                    totalRevenue = totalRevenue,
                    averageSubscriptionDays = avgDays,
                    recentSubscriptions = recentSubs
                )
            Result.success(analytics)
        } catch (e: Exception) {
            logger.error("Failed to get subscription analytics", e)
            Result.error("Failed to get analytics: ${e.message}", e)
        }
    }

    override suspend fun cancelUserSubscription(
        adminEmail: String,
        targetUserEmail: String
    ): Result<SubscriptionResponse> {
        logger.debug("Admin $adminEmail cancelling subscription for user: $targetUserEmail")
        return cancelSubscription(targetUserEmail)
    }

    override suspend fun processExpiredSubscriptions(): Result<Int> {
        logger.info("Processing expired subscriptions")
        return try {
            val usersResult = userRepository.getAllUsers()
            if (usersResult is Result.Error) {
                return Result.error("Failed to get users: ${usersResult.message}")
            }

            val (users, _) = (usersResult as Result.Success).data
            val now = Instant.now()
            var processedCount = 0

            for (user in users) {
                var hasChanges = false
                val updatedSubscriptions =
                    user.subscriptions.map { subscription ->
                        if (subscription.status == SubscriptionStatus.ACTIVE) {
                            try {
                                val endDate = Instant.parse(subscription.endDate)
                                if (now.isAfter(endDate)) {
                                    hasChanges = true
                                    processedCount++
                                    val gracePeriodEnd =
                                        endDate.plusSeconds(Environment.getGracePeriodHours() * 3600)
                                    subscription.copy(
                                        status = SubscriptionStatus.GRACE_PERIOD,
                                        gracePeriodEnd = gracePeriodEnd.toString()
                                    )
                                } else {
                                    subscription
                                }
                            } catch (e: Exception) {
                                logger.warn(
                                    "Failed to parse end date for subscription: ${subscription.createdAt}",
                                    e
                                )
                                subscription
                            }
                        } else {
                            subscription
                        }
                    }

                if (hasChanges) {
                    val updatedUser = user.copy(subscriptions = updatedSubscriptions)
                    userRepository.updateUser(updatedUser)
                }
            }

            logger.info("Processed $processedCount expired subscriptions")
            Result.success(processedCount)
        } catch (e: Exception) {
            logger.error("Failed to process expired subscriptions", e)
            Result.error("Failed to process expired subscriptions: ${e.message}", e)
        }
    }

    override suspend fun processGracePeriodExpiry(): Result<Int> {
        logger.info("Processing grace period expiry")
        return try {
            val usersResult = userRepository.getAllUsers()
            if (usersResult is Result.Error) {
                return Result.error("Failed to get users: ${usersResult.message}")
            }

            val (users, _) = (usersResult as Result.Success).data
            val now = Instant.now()
            var processedCount = 0

            for (user in users) {
                var hasChanges = false
                val updatedSubscriptions =
                    user.subscriptions.map { subscription ->
                        if (subscription.status == SubscriptionStatus.GRACE_PERIOD &&
                            subscription.gracePeriodEnd != null
                        ) {
                            try {
                                val gracePeriodEnd = Instant.parse(subscription.gracePeriodEnd)
                                if (now.isAfter(gracePeriodEnd)) {
                                    hasChanges = true
                                    processedCount++
                                    subscription.copy(status = SubscriptionStatus.EXPIRED)
                                } else {
                                    subscription
                                }
                            } catch (e: Exception) {
                                logger.warn(
                                    "Failed to parse grace period end for subscription: ${subscription.createdAt}",
                                    e
                                )
                                subscription
                            }
                        } else {
                            subscription
                        }
                    }

                if (hasChanges) {
                    val hasActiveOrGrace =
                        updatedSubscriptions.any {
                            it.status == SubscriptionStatus.ACTIVE ||
                                it.status == SubscriptionStatus.GRACE_PERIOD
                        }
                    val updatedUser =
                        user.copy(
                            subscriptions = updatedSubscriptions,
                            isPremium = hasActiveOrGrace
                        )
                    userRepository.updateUser(updatedUser)
                }
            }

            logger.info("Processed $processedCount grace period expirations")
            Result.success(processedCount)
        } catch (e: Exception) {
            logger.error("Failed to process grace period expiry", e)
            Result.error("Failed to process grace period expiry: ${e.message}", e)
        }
    }

    private fun findActiveOrGracePeriodSubscription(
        subscriptions: List<Subscription>
    ): Subscription? {
        return subscriptions.find {
            it.status == SubscriptionStatus.ACTIVE || it.status == SubscriptionStatus.GRACE_PERIOD
        }
    }

    private fun mapToSubscriptionResponse(subscription: Subscription): SubscriptionResponse {
        val daysRemaining = calculateDaysRemaining(subscription.endDate)
        val isInGracePeriod = subscription.status == SubscriptionStatus.GRACE_PERIOD

        return SubscriptionResponse(
            service = subscription.service,
            startDate = subscription.startDate,
            endDate = subscription.endDate,
            status = subscription.status,
            daysRemaining = daysRemaining,
            isInGracePeriod = isInGracePeriod,
            sourcePaymentId = subscription.sourcePaymentId
        )
    }

    private fun calculateDaysRemaining(endDateStr: String): Long? {
        return try {
            val endDate = Instant.parse(endDateStr)
            val now = Instant.now()
            val duration = Duration.between(now, endDate)
            if (duration.isNegative) 0L else duration.toDays()
        } catch (e: Exception) {
            logger.warn("Failed to calculate days remaining for end date: $endDateStr", e)
            null
        }
    }
}
