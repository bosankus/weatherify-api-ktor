package domain.service

import com.google.firebase.messaging.*
import config.FirebaseAdmin
import org.slf4j.LoggerFactory

/**
 * Service for sending push notifications via Firebase Cloud Messaging (FCM).
 */
interface NotificationService {
    /**
     * Send a push notification to a specific device token.
     *
     * @param token The FCM device token
     * @param title The notification title
     * @param body The notification body
     * @return Result with message ID on success, or error message on failure
     */
    suspend fun sendNotification(token: String, title: String, body: String): Result<String>
}

class UnregisteredFcmTokenException(message: String) : Exception(message)

class NotificationServiceImpl : NotificationService {
    private val logger = LoggerFactory.getLogger(NotificationServiceImpl::class.java)

    override suspend fun sendNotification(token: String, title: String, body: String): Result<String> {
        return try {
            // Check if Firebase is initialized
            if (!FirebaseAdmin.isInitialized()) {
                logger.error("Firebase Admin SDK is not initialized. Cannot send notification.")
                return Result.failure(Exception("Firebase Admin SDK is not initialized"))
            }

            // Build the notification message
            val notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build()

            val message = Message.builder()
                .setToken(token)
                .setNotification(notification)
                .build()

            // Send the message
            val messageId = FirebaseMessaging.getInstance().send(message)

            logger.info("Successfully sent notification. Message ID: $messageId")
            Result.success(messageId)
        } catch (e: FirebaseMessagingException) {
            val errorCode = e.messagingErrorCode
            if (errorCode == MessagingErrorCode.UNREGISTERED) {
                logger.warn("FCM token is unregistered. Token: $token")
                Result.failure(UnregisteredFcmTokenException("FCM token is unregistered"))
            } else {
                logger.error(
                    "Failed to send notification. FCM error code: $errorCode, token: $token",
                    e
                )
                Result.failure(e)
            }
        } catch (e: Exception) {
            logger.error("Failed to send notification to token: $token", e)
            Result.failure(e)
        }
    }
}
