package bose.ankush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.time.Instant

/**
 * Refund status values
 */
@Serializable
enum class RefundStatus {
    PENDING,
    PROCESSED,
    FAILED
}

/**
 * Refund speed options
 */
@Serializable
enum class RefundSpeed {
    OPTIMUM,    // Instant refund if eligible
    NORMAL      // Standard 5-7 days
}

/**
 * Refund model stored in database
 */
@Serializable
data class Refund(
    @SerialName("_id")
    val id: String = ObjectId().toHexString(),

    // Razorpay refund details
    val refundId: String,                    // Razorpay refund ID (rfnd_xxx)
    val paymentId: String,                   // Original payment ID (pay_xxx)
    val orderId: String?,                    // Original order ID

    // Amount details
    val amount: Int,                         // Amount in paise (smallest currency unit)
    val currency: String = "INR",

    // Refund metadata
    val status: RefundStatus,                // pending, processed, failed
    val speedRequested: RefundSpeed,         // optimum, normal
    val speedProcessed: RefundSpeed?,        // actual speed used by Razorpay

    // User and admin tracking
    val userEmail: String,
    val userId: String?,
    val processedBy: String,                 // Admin email who initiated refund

    // Audit fields
    val reason: String? = null,
    val notes: String? = null,               // Changed from Map to String for simpler storage
    val receipt: String? = null,

    // Timestamps
    val createdAt: String = Instant.now().toString(),
    val processedAt: String? = null,
    val failedAt: String? = null,

    // Razorpay response data
    val acquirerData: Map<String, String?>? = null,  // Values can be null
    val batchId: String? = null,

    // Error tracking
    val errorCode: String? = null,
    val errorDescription: String? = null
)

/**
 * Request to initiate a refund
 */
@Serializable
data class InitiateRefundRequest(
    val paymentId: String,
    val amount: Int?,                        // null for full refund
    val speed: RefundSpeed = RefundSpeed.OPTIMUM,
    val reason: String? = null,
    val notes: String? = null,               // Changed from Map to String for simpler API
    val receipt: String? = null
)

/**
 * Response for refund operations
 */
@Serializable
data class RefundResponse(
    val success: Boolean,
    val message: String,
    val refund: RefundDto? = null
)

/**
 * Refund DTO for API responses
 */
@Serializable
data class RefundDto(
    val refundId: String,
    val paymentId: String,
    val amount: Double,                      // Amount in rupees for display
    val currency: String,
    val status: RefundStatus,
    val speedRequested: RefundSpeed,
    val speedProcessed: RefundSpeed?,
    val userEmail: String,
    val processedBy: String,
    val reason: String?,
    val createdAt: String,
    val processedAt: String?
)

/**
 * Refund metrics for dashboard analytics
 */
@Serializable
data class RefundMetrics(
    val totalRefunds: Double,                // Total refunded amount
    val monthlyRefunds: Double,              // Current month refunds
    val refundRate: Double,                  // (total refunds / total revenue) * 100
    val totalRefundCount: Int,
    val monthlyRefundCount: Int,
    val instantRefundCount: Int,
    val normalRefundCount: Int,
    val averageProcessingTimeHours: Double,
    val monthlyRefundChart: List<MonthlyRefundData>
)

/**
 * Monthly refund data point for charts
 */
@Serializable
data class MonthlyRefundData(
    val month: String,                       // Format: "2025-01"
    val refundAmount: Double,
    val refundCount: Int
)

/**
 * Refund history response with pagination
 */
@Serializable
data class RefundHistoryResponse(
    val refunds: List<RefundDto>,
    val pagination: PaginationInfo
)

/**
 * Payment refund summary showing all refunds for a payment
 */
@Serializable
data class PaymentRefundSummary(
    val paymentId: String,
    val originalAmount: Int,
    val totalRefunded: Int,
    val remainingRefundable: Int,
    val refunds: List<RefundDto>,
    val isFullyRefunded: Boolean
)

/**
 * Internal Razorpay refund request model
 */
@Serializable
internal data class RazorpayRefundRequest(
    val amount: Int? = null,                 // null for full refund
    val speed: String = "optimum",           // "optimum" or "normal"
    val notes: Map<String, String>? = null,
    val receipt: String? = null
)

/**
 * Internal Razorpay refund response model
 * Note: Razorpay inconsistently sends [] (empty array) instead of {} or null for empty map fields
 * Note: Map values can be null (e.g., {"arn": null})
 */
@Serializable
internal data class RazorpayRefundResponse(
    val id: String,                          // refund_id
    val entity: String,                      // "refund"
    val amount: Int,
    val currency: String,
    val payment_id: String,
    val notes: Map<String, String?>? = null,  // Can be [], {}, null, or contain null values
    val receipt: String? = null,
    val acquirer_data: Map<String, String?>? = null,  // Can be [], {}, null, or contain null values
    val created_at: Long,
    val batch_id: String? = null,
    val status: String,                      // "pending", "processed", "failed"
    val speed_processed: String,             // "instant", "normal"
    val speed_requested: String              // "optimum", "normal"
)

/**
 * Razorpay refunds list response model
 */
@Serializable
internal data class RazorpayRefundsListResponse(
    val entity: String,                      // "collection"
    val count: Int,
    val items: List<RazorpayRefundResponse>
)

/**
 * Internal Razorpay webhook payload model
 */
@Serializable
internal data class RazorpayWebhookPayload(
    val entity: String,                      // "event"
    val account_id: String,
    val event: String,                       // "refund.processed", "refund.failed"
    val contains: List<String>,
    val payload: RazorpayWebhookRefundPayload,
    val created_at: Long
)

/**
 * Internal Razorpay webhook refund payload
 */
@Serializable
internal data class RazorpayWebhookRefundPayload(
    val refund: RazorpayWebhookRefundEntity
)

/**
 * Internal Razorpay webhook refund entity
 */
@Serializable
internal data class RazorpayWebhookRefundEntity(
    val entity: RazorpayRefundResponse
)
