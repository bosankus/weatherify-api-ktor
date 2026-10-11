package domain.service

import org.slf4j.LoggerFactory

/**
 * Tells every signed-in device of an account that something changed, with a silent data push.
 * Devices react by fetching GET /account again; the push carries no account data itself.
 */
class AccountChangeNotifier(
    private val tokens: PushTokenStore,
    private val notifications: NotificationService,
) {
    private val logger = LoggerFactory.getLogger(AccountChangeNotifier::class.java)

    /** Best effort: a failed push only means devices see the change on their next fetch. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun photoChanged(email: String) {
        try {
            val targets = tokens.tokens(email)
            if (targets.isEmpty()) return
            val unregistered = notifications
                .sendData(targets, mapOf(TYPE_KEY to PHOTO_CHANGED))
                .getOrElse {
                    logger.warn("Account photo push failed for $email: ${it.message}")
                    return
                }
            unregistered.forEach { tokens.remove(email, it) }
        } catch (e: Exception) {
            logger.warn("Account photo push failed for $email", e)
        }
    }

    companion object {
        const val TYPE_KEY = "type"
        const val PHOTO_CHANGED = "account_photo_changed"
    }
}
