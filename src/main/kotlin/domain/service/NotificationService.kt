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

    /**
     * Send a silent, data-only message to [tokens]: no notification is shown. Android delivers it
     * at high priority; iOS gets it as a background (content-available) push.
     *
     * @return the tokens FCM reported as unregistered, for the caller to forget
     */
    suspend fun sendData(tokens: List<String>, data: Map<String, String>): Result<List<String>>
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

    override suspend fun sendData(tokens: List<String>, data: Map<String, String>): Result<List<String>> {
        if (tokens.isEmpty()) return Result.success(emptyList())
        if (!FirebaseAdmin.isInitialized()) {
            return Result.failure(Exception("Firebase Admin SDK is not initialized"))
        }
        return try {
            val message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .putAllData(data)
                .setAndroidConfig(
                    AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build()
                )
                .setApnsConfig(
                    ApnsConfig.builder()
                        .putHeader("apns-push-type", "background")
                        .putHeader("apns-priority", "5")
                        .setAps(Aps.builder().setContentAvailable(true).build())
                        .build()
                )
                .build()
            val batch = FirebaseMessaging.getInstance().sendEachForMulticast(message)
            val unregistered = batch.responses.mapIndexedNotNull { index, response ->
                tokens[index].takeIf {
                    response.exception?.messagingErrorCode == MessagingErrorCode.UNREGISTERED
                }
            }
            logger.info(
                "Data push sent: ${batch.successCount} ok, ${batch.failureCount} failed, " +
                    "${unregistered.size} unregistered"
            )
            Result.success(unregistered)
        } catch (e: Exception) {
            logger.error("Failed to send data push", e)
            Result.failure(e)
        }
    }
}
