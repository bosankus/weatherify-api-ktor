package bose.ankush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.time.Instant

/**
 * Service status values for catalog management
 */
@Serializable
enum class ServiceStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}

/**
 * Duration type for pricing tiers
 */
@Serializable
enum class DurationType {
    DAYS,
    MONTHS,
    YEARS
}

/**
 * Limit type for service quotas
 */
@Serializable
enum class LimitType {
    HARD,
    SOFT
}

/**
 * Change type for service history tracking
 */
@Serializable
enum class ChangeType {
    CREATED,
    UPDATED,
    STATUS_CHANGED,
    ARCHIVED,
    RESTORED
}

/**
 * Pricing tier configuration for a service
 */
@Serializable
data class PricingTier(
    val id: String = ObjectId().toHexString(),
    val amount: Int,  // Amount in smallest currency unit (paise for INR)
    val currency: String = "INR",
    val duration: Int,  // Numeric value
    val durationType: DurationType,  // DAYS, MONTHS, YEARS
    val isDefault: Boolean = false,
    val isFeatured: Boolean = false,
    val displayOrder: Int = 0
)

/**
 * Service feature description
 */
@Serializable
data class ServiceFeature(
    val id: String = ObjectId().toHexString(),
    val description: String,
    val isHighlighted: Boolean = false,
    val displayOrder: Int = 0
)

/**
 * Service limit configuration for quotas
 */
@Serializable
data class ServiceLimit(
    val value: Long,
    val type: LimitType,  // HARD, SOFT
    val unit: String  // e.g., "requests/day", "GB", "users"
)

/**
 * Complete service configuration model
 */
@Serializable
data class ServiceConfig(
    @SerialName("_id")
    val id: String = ObjectId().toHexString(),
    val serviceCode: String,  // Unique identifier (e.g., PREMIUM_ONE, PREMIUM_PLUS)
    val displayName: String,  // User-friendly name
    val description: String,
    val pricingTiers: List<PricingTier>,
    val features: List<ServiceFeature>,
    val status: ServiceStatus,
    val limits: Map<String, ServiceLimit> = emptyMap(),
    val availabilityStart: String? = null,
    val availabilityEnd: String? = null,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String,  // Admin email
    val updatedBy: String   // Admin email
)

/**
 * Change detail for service history
 */
@Serializable
data class ChangeDetail(
    val field: String,
    val oldValue: String?,
    val newValue: String?
)

/**
 * Service history record for audit trail
 */
@Serializable
data class ServiceHistory(
    @SerialName("_id")
    val id: String = ObjectId().toHexString(),
    val serviceId: String,
    val serviceCode: String,
    val changeType: ChangeType,
    val changedBy: String,  // Admin email
    val changedAt: String = Instant.now().toString(),
    val changes: Map<String, ChangeDetail>
)

/**
 * Request model for creating a new service
 */
@Serializable
data class CreateServiceRequest(
    val serviceCode: String,
    val displayName: String,
    val description: String,
    val pricingTiers: List<PricingTier>,
    val features: List<ServiceFeature>,
    val limits: Map<String, ServiceLimit> = emptyMap(),
    val availabilityStart: String? = null,
    val availabilityEnd: String? = null
)

/**
 * Request model for updating an existing service
 */
@Serializable
data class UpdateServiceRequest(
    val displayName: String? = null,
    val description: String? = null,
    val pricingTiers: List<PricingTier>? = null,
    val features: List<ServiceFeature>? = null,
    val limits: Map<String, ServiceLimit>? = null,
    val availabilityStart: String? = null,
    val availabilityEnd: String? = null
)

/**
 * Service summary for list view
 */
@Serializable
data class ServiceSummary(
    val id: String,
    val serviceCode: String,
    val displayName: String,
    val status: ServiceStatus,
    val activeSubscriptions: Long,
    val lowestPrice: Int,
    val currency: String,
    val createdAt: String
)

/**
 * Response model for service list with pagination
 */
@Serializable
data class ServiceListResponse(
    val services: List<ServiceSummary>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int
)

/**
 * Monthly subscription data for analytics
 */
@Serializable
data class MonthlySubscriptionData(
    val month: String,
    val count: Long,
    val revenue: Double
)

/**
 * Service analytics data
 */
@Serializable
data class ServiceAnalytics(
    val activeSubscriptions: Long,
    val totalSubscriptions: Long,
    val totalRevenue: Double,
    val monthlyTrend: List<MonthlySubscriptionData>,
    val popularPricingTier: String?,
    val averageDuration: Double
)

/**
 * Detailed service response with analytics and history
 */
@Serializable
data class ServiceDetailResponse(
    val service: ServiceConfig,
    val analytics: ServiceAnalytics,
    val history: List<ServiceHistory>
)

/**
 * Request model for changing service status
 */
@Serializable
data class ChangeServiceStatusRequest(
    val status: ServiceStatus
)

/**
 * Request model for cloning a service
 */
@Serializable
data class CloneServiceRequest(
    val newServiceCode: String
)
