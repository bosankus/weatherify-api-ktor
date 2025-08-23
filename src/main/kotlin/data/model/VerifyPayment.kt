package bose.ankush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyPaymentRequest(
    @SerialName("razorpay_payment_id") val paymentId: String? = null,
    @SerialName("razorpay_order_id") val orderId: String? = null,
    @SerialName("razorpay_signature") val signature: String? = null,
)

@Serializable
data class VerifyPaymentResponse(
    val verified: Boolean,
)
