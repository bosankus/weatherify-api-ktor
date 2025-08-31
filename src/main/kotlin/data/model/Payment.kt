package bose.ankush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.time.Instant

/**
 * Payment model stored after successful verification.
 * Uses userEmail as the common field to link with User.
 *
 * Refactored to include optional admin-tracking details without changing existing fields.
 */
@Serializable
data class Payment(
    @SerialName("_id")
    val id: String = ObjectId().toHexString(),
    val userEmail: String,
    @SerialName("razorpay_order_id") val orderId: String,
    @SerialName("razorpay_payment_id") val paymentId: String,
    @SerialName("razorpay_signature") val signature: String,
    val amount: Int? = null,
    val currency: String? = null,
    val receipt: String? = null,
    val status: String? = "verified",
    val notes: Map<String, String>? = null,
    val createdAt: String = Instant.now().toString(),
    val verifiedAt: String? = createdAt,
    val userId: String? = null,
    val serviceType: ServiceType? = null,
    val subscriptionStart: String? = null,
    val subscriptionEnd: String? = null,
    val requestIp: String? = null,
    val userAgent: String? = null,
)
