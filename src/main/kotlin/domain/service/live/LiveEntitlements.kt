package domain.service.live

import com.androidplay.weatherify.repository.UserRepository
import domain.model.SubscriptionFeature
import domain.model.SubscriptionFeatureResolver
import domain.model.live.LiveChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * What a connection is allowed to do, resolved once at handshake from the user's subscription.
 *
 * Resolved at connect time and then cached for the life of the connection. That is a deliberate
 * trade-off: a user who upgrades mid-session keeps free-tier cadence until they reconnect.
 * The alternative — re-checking entitlements on every push — would put a database read in the
 * hot fan-out path, which is exactly where you cannot afford one.
 */
data class LiveEntitlements(
    val tier: String,
    val allowedChannels: List<String>,
    val cadenceSeconds: Int,
    val maxTopics: Int
) {
    fun allows(channel: String): Boolean = channel in allowedChannels

    companion object {
        val FREE = LiveEntitlements(
            tier = "FREE",
            allowedChannels = listOf(LiveChannel.CONDITIONS, LiveChannel.ALERTS),
            cadenceSeconds = 60,
            maxTopics = 3
        )

        val PREMIUM = LiveEntitlements(
            tier = "PREMIUM",
            allowedChannels = LiveChannel.ALL,
            cadenceSeconds = 15,
            maxTopics = 20
        )
    }
}

/**
 * Resolves entitlements, with a short TTL cache so a reconnect storm (which is exactly what a
 * network blip produces) doesn't turn into a Mongo read storm.
 */
class LiveEntitlementResolver(
    private val userRepository: UserRepository,
    private val ttlMillis: Long = 5 * 60 * 1000L
) {
    private data class Entry(val value: LiveEntitlements, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    suspend fun resolve(email: String): LiveEntitlements {
        val now = System.currentTimeMillis()
        cache[email]?.let { if (now < it.expiresAt) return it.value }

        val user = userRepository.findUserByEmail(email).getOrNull()
        val features = user?.let { SubscriptionFeatureResolver.resolveFeatures(it) } ?: emptySet()

        // Air quality is the paid marker in the existing catalogue, so it decides the tier here too.
        val entitlements =
            if (SubscriptionFeature.AIR_QUALITY in features) LiveEntitlements.PREMIUM
            else LiveEntitlements.FREE

        cache[email] = Entry(entitlements, now + ttlMillis)
        return entitlements
    }

    fun invalidate(email: String) {
        cache.remove(email)
    }
}
