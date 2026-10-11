package domain.service

import com.androidplay.weatherify.domain.User
import com.androidplay.weatherify.repository.UserRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Sends a visible notification to every device of a user: each token in `pushTokens` (one per
 * signed-in device) plus the legacy `fcmToken` saved at sign-up, for clients that predate
 * per-device registration. Tokens FCM reports as unregistered are forgotten.
 */
class UserPushNotifier(
    private val tokens: PushTokenStore,
    private val users: UserRepository,
    private val notifications: NotificationService,
) {
    private val logger = LoggerFactory.getLogger(UserPushNotifier::class.java)

    suspend fun notify(
        user: User,
        title: String,
        body: String,
        imageUrl: String? = null,
    ): Result<NotificationFanout> {
        val email = user.email
        val legacy = user.fcmToken?.trim()
        val targets = (tokens.tokens(email) + listOfNotNull(legacy)).filter { it.isNotEmpty() }.distinct()
        if (targets.isEmpty()) return Result.failure(NoPushTokenException(email))

        return notifications.sendNotification(targets, title, body, imageUrl).onSuccess { fanout ->
            fanout.unregistered.forEach { dead ->
                tokens.remove(email, dead)
                if (dead == legacy) users.clearFcmTokenByEmail(email)
            }
        }
    }

    /** Best effort: logs instead of failing, for pushes that ride along with another action. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun notifyQuietly(user: User, title: String, body: String) {
        try {
            notify(user, title, body)
                .onSuccess { logger.info("Push \"$title\" reached ${it.successCount} devices of ${user.email}") }
                .onFailure { logger.warn("Push \"$title\" to ${user.email} failed: ${it.message}") }
        } catch (e: Exception) {
            logger.warn("Push \"$title\" to ${user.email} failed", e)
        }
    }

    /** "Your Premium One is now active. Expiring Oct 11, 2027." */
    suspend fun premiumActivated(user: User, serviceName: String, expiresAt: String) {
        val expiry = try {
            EXPIRY_FORMAT.format(Instant.parse(expiresAt))
        } catch (_: Exception) {
            expiresAt
        }
        notifyQuietly(user, "Subscription activated", "Your $serviceName is now active. Expiring $expiry.")
    }

    companion object {
        private val EXPIRY_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneOffset.UTC)
    }
}

class NoPushTokenException(email: String) : Exception("No FCM token registered for $email")
