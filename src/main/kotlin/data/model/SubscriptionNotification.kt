package bose.ankush.data.model

import kotlinx.serialization.Serializable

/**
 * Payload for FCM push notifications related to subscription expiry
 */
@Serializable
data class SubscriptionNotificationPayload(
    val title: String,
    val body: String,
    val daysRemaining: Int? = null,
    val endDate: String? = null,
    val action: String = "RENEW_SUBSCRIPTION"
)
