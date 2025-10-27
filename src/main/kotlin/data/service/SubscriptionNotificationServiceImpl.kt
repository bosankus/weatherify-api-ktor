package data.service

import bose.ankush.data.model.*
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import config.FirebaseAdmin
import domain.model.Result
import domain.repository.UserRepository
import domain.service.SubscriptionNotificationService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class SubscriptionNotificationServiceImpl(
    private val userRepository: UserRepository
) : SubscriptionNotificationService {
    private val logger = LoggerFactory.getLogger(SubscriptionNotificationServiceImpl::class.java)

    override suspend fun sendExpiryWarning(
        userEmail: String,
        fcmToken: String,
        daysRemaining: Int,
        endDate: String
    ): Result<Boolean> {
        logger.debug("Sending expiry warning to $userEmail: $daysRemaining days remaining")

        if (!FirebaseAdmin.isInitialized()) {
            logger.warn("Firebase not initialized, skipping notification for $userEmail")
            return Result.error("Firebase not initialized")
        }

        return try {
            val title = "Subscription Expiring Soon"
            val dayText = if (daysRemaining > 1) "s" else ""
            val body = "Your premium subscription expires in $daysRemaining day$dayText. Renew now!"

            val message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("action", "RENEW_SUBSCRIPTION")
                .putData("daysRemaining", daysRemaining.toString())
                .putData("endDate", endDate)
                .build()

            val messageId = FirebaseMessaging.getInstance().send(message)
            logger.info("Expiry warning sent to $userEmail: $messageId")
            Result.success(true)
        } catch (e: Exception) {
            logger.error("Failed to send expiry warning to $userEmail", e)
            Result.error("Failed to send notification: ${e.message}", e)
        }
    }

    override suspend fun sendExpiryNotification(
        userEmail: String,
        fcmToken: String
    ): Result<Boolean> {
        logger.debug("Sending expiry notification to $userEmail")

        if (!FirebaseAdmin.isInitialized()) {
            logger.warn("Firebase not initialized, skipping notification for $userEmail")
            return Result.error("Firebase not initialized")
        }

        return try {
            val title = "Subscription Expired"
            val body = "Your premium subscription has expired. Renew now to regain access!"

            val message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("action", "RENEW_SUBSCRIPTION")
                .build()

            val messageId = FirebaseMessaging.getInstance().send(message)
            logger.info("Expiry notification sent to $userEmail: $messageId")
            Result.success(true)
        } catch (e: Exception) {
            logger.error("Failed to send expiry notification to $userEmail", e)
            Result.error("Failed to send notification: ${e.message}", e)
        }
    }

    override suspend fun processExpiryNotifications(): Result<Int> {
        logger.info("Processing subscription expiry notifications")

        if (!FirebaseAdmin.isInitialized()) {
            logger.warn("Firebase not initialized, skipping notification processing")
            return Result.error("Firebase not initialized")
        }

        return try {
            val usersResult = userRepository.getAllUsers()
            if (usersResult is Result.Error) {
                return Result.error("Failed to get users: ${usersResult.message}")
            }

            val (users, _) = (usersResult as Result.Success).data
            val now = Instant.now()
            var notificationsSent = 0

            for (user in users) {
                if (user.fcmToken.isNullOrBlank()) continue

                val activeSubscriptions = user.subscriptions.filter {
                    it.status == SubscriptionStatus.ACTIVE || it.status == SubscriptionStatus.GRACE_PERIOD
                }

                for (subscription in activeSubscriptions) {
                    try {
                        val endDate = Instant.parse(subscription.endDate)
                        val daysRemaining = Duration.between(now, endDate).toDays()

                        val notificationType = when {
                            daysRemaining == 3L -> NotificationType.EXPIRY_WARNING_3_DAYS
                            daysRemaining == 1L -> NotificationType.EXPIRY_WARNING_1_DAY
                            daysRemaining <= 0L && subscription.status == SubscriptionStatus.GRACE_PERIOD ->
                                NotificationType.SUBSCRIPTION_EXPIRED

                            else -> null
                        }

                        if (notificationType != null) {
                            val alreadySent = user.notificationsSent.any {
                                it.subscriptionId == subscription.createdAt && it.type == notificationType
                            }

                            if (!alreadySent) {
                                val result = when (notificationType) {
                                    NotificationType.EXPIRY_WARNING_3_DAYS,
                                    NotificationType.EXPIRY_WARNING_1_DAY -> {
                                        sendExpiryWarning(
                                            user.email,
                                            user.fcmToken!!,
                                            daysRemaining.toInt(),
                                            subscription.endDate
                                        )
                                    }

                                    NotificationType.SUBSCRIPTION_EXPIRED -> {
                                        sendExpiryNotification(user.email, user.fcmToken!!)
                                    }
                                }

                                if (result is Result.Success) {
                                    val notificationRecord = NotificationRecord(
                                        type = notificationType,
                                        sentAt = now.toString(),
                                        subscriptionId = subscription.createdAt
                                    )

                                    val updatedUser = user.copy(
                                        notificationsSent = user.notificationsSent + notificationRecord
                                    )

                                    userRepository.updateUser(updatedUser)
                                    notificationsSent++
                                    logger.info("Sent $notificationType notification to ${user.email}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to process subscription for user ${user.email}", e)
                    }
                }
            }

            logger.info("Processed expiry notifications: $notificationsSent sent")
            Result.success(notificationsSent)
        } catch (e: Exception) {
            logger.error("Failed to process expiry notifications", e)
            Result.error("Failed to process notifications: ${e.message}", e)
        }
    }
}
