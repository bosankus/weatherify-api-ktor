package bose.ankush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderRequest(
    // Amount in smallest currency unit (e.g., paise)
    val amount: Int,
    val currency: String,
    val receipt: String,
    @SerialName("partial_payment") val partialPayment: Boolean? = null,
    @SerialName("first_payment_min_amount") val firstPaymentMinAmount: Int? = null,
    // Optional notes to attach to the order
    val notes: Map<String, String>? = null
)

@Serializable
internal data class RazorpayCreateOrderPayload(
    val amount: Int,
    val currency: String,
    val receipt: String,
    @SerialName("partial_payment") val partialPayment: Boolean? = null,
    @SerialName("first_payment_min_amount") val firstPaymentMinAmount: Int? = null,
    val notes: Map<String, String>? = null
)

@Serializable
internal data class RazorpayOrderResponse(
    val id: String,
    val entity: String? = null,
    val amount: Int,
    @SerialName("amount_paid") val amountPaid: Int? = null,
    @SerialName("amount_due") val amountDue: Int? = null,
    val currency: String,
    val receipt: String? = null,
    @SerialName("offer_id") val offerId: String? = null,
    val status: String? = null,
    val attempts: Int? = null,
    @SerialName("created_at") val createdAt: Long? = null
)

@Serializable
internal data class RazorpayErrorDetail(
    val code: String? = null,
    val description: String? = null,
    val source: String? = null,
    val step: String? = null,
    val reason: String? = null,
    val field: String? = null,
)

@Serializable
internal data class RazorpayErrorResponse(
    val error: RazorpayErrorDetail? = null
)

@Serializable
data class CreateOrderResponse(
    @SerialName("order_id") val orderId: String,
    val amount: Int,
    val currency: String,
    val receipt: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)
