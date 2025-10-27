package domain.service

import bose.ankush.data.model.*
import domain.model.Result

/**
 * Service interface for subscription management operations.
 * Handles subscription lifecycle, status determination, and analytics.
 */
interface SubscriptionService {
    /**
     * Get current subscription status for a user.
     * @param userEmail The user's email
     * @return Result containing subscription response or error
     */
    suspend fun getSubscriptionStatus(userEmail: String): Result<SubscriptionResponse>

    /**
     * Get subscription history for a user.
     * @param userEmail The user's email
     * @return Result containing subscription history response or error
     */
    suspend fun getSubscriptionHistory(userEmail: String): Result<SubscriptionHistoryResponse>

    /**
     * Cancel active subscription for a user.
     * @param userEmail The user's email
     * @return Result containing updated subscription response or error
     */
    suspend fun cancelSubscription(userEmail: String): Result<SubscriptionResponse>

    /**
     * Get all subscriptions with pagination and optional status filter (admin).
     * @param page Page number (1-indexed)
     * @param pageSize Number of items per page
     * @param statusFilter Optional status filter
     * @return Result containing paginated subscription list or error
     */
    suspend fun getAllSubscriptions(
        page: Int,
        pageSize: Int,
        statusFilter: SubscriptionStatus? = null
    ): Result<AdminSubscriptionListResponse>

    /**
     * Get subscription analytics and metrics (admin).
     * @return Result containing subscription analytics or error
     */
    suspend fun getSubscriptionAnalytics(): Result<SubscriptionAnalytics>

    /**
     * Cancel a user's subscription (admin operation).
     * @param adminEmail The admin's email
     * @param targetUserEmail The target user's email
     * @return Result containing updated subscription response or error
     */
    suspend fun cancelUserSubscription(
        adminEmail: String,
        targetUserEmail: String
    ): Result<SubscriptionResponse>

    /**
     * Process expired subscriptions (background job).
     * Updates subscriptions that have passed their end date.
     * @return Result containing count of processed subscriptions or error
     */
    suspend fun processExpiredSubscriptions(): Result<Int>

    /**
     * Process grace period expiry (background job).
     * Updates subscriptions that have exceeded grace period.
     * @return Result containing count of processed subscriptions or error
     */
    suspend fun processGracePeriodExpiry(): Result<Int>
}
