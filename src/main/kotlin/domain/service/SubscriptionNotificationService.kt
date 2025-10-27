package domain.service

import domain.model.Result

/**
 * Service interface for sending subscription expiry notifications via FCM.
 * Handles notification logic for subscription warnings and expiry alerts.
 */
interface SubscriptionNotificationService {
    /**
     * Send an expiry warning notification to a user.
     * @param userEmail The user's email
     * @param fcmToken The user's FCM token
     * @param daysRemaining Number of days until subscription expires
     * @param endDate The subscription end date
     * @return Result containing true if notification was sent successfully
     */
    suspend fun sendExpiryWarning(
        userEmail: String,
        fcmToken: String,
        daysRemaining: Int,
        endDate: String
    ): Result<Boolean>

    /**
     * Send a subscription expired notification to a user.
     * @param userEmail The user's email
     * @param fcmToken The user's FCM token
     * @return Result containing true if notification was sent successfully
     */
    suspend fun sendExpiryNotification(
        userEmail: String,
        fcmToken: String
    ): Result<Boolean>

    /**
     * Process all subscriptions and send appropriate expiry notifications.
     * This method checks all active subscriptions and sends notifications
     * for those expiring in 3 days, 1 day, or already expired.
     *
     * @return Result containing count of notifications sent
     */
    suspend fun processExpiryNotifications(): Result<Int>
}
