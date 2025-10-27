package bose.ankush.data.model

import kotlinx.serialization.Serializable

/** Response model for subscription status */
@Serializable
data class SubscriptionResponse(
    val service: ServiceType,
    val startDate: String,
    val endDate: String,
    val status: SubscriptionStatus,
    val daysRemaining: Long?,
    val isInGracePeriod: Boolean,
    val sourcePaymentId: String?
)

/** Response model for subscription history */
@Serializable
data class SubscriptionHistoryResponse(
    val subscriptions: List<SubscriptionResponse>,
    val totalCount: Int
)

/** Admin view of a subscription with user and payment details */
@Serializable
data class AdminSubscriptionView(
    val userEmail: String,
    val userId: String,
    val service: ServiceType,
    val startDate: String,
    val endDate: String,
    val status: SubscriptionStatus,
    val daysRemaining: Long?,
    val paymentId: String?,
    val amount: Int?,
    val currency: String?,
    val createdAt: String
)

/** Response model for admin subscription list with pagination */
@Serializable
data class AdminSubscriptionListResponse(
    val subscriptions: List<AdminSubscriptionView>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int
)

/** Subscription analytics and metrics */
@Serializable
data class SubscriptionAnalytics(
    val totalActive: Long,
    val totalExpired: Long,
    val totalCancelled: Long,
    val totalRevenue: Double,
    val averageSubscriptionDays: Double,
    val recentSubscriptions: List<RecentSubscription>
)

/** Recent subscription for analytics */
@Serializable
data class RecentSubscription(
    val userEmail: String,
    val service: ServiceType,
    val startDate: String,
    val amount: Int?,
    val status: SubscriptionStatus
)
