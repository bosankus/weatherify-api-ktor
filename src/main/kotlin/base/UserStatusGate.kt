package bose.ankush.base

import com.androidplay.core.common.Result
import com.androidplay.weatherify.repository.UserRepository
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-request check that the user behind a still-valid JWT is allowed to act: the account must
 * exist, be active and not be deleted. JWTs are stateless, so without this a deleted or
 * deactivated user's token keeps working until it expires.
 *
 * Results are cached for [TTL_MILLIS] to avoid a DB read on every request; call [invalidate]
 * when an account's status changes so this instance reflects it immediately. Other instances
 * catch up within the TTL.
 */
object UserStatusGate {
    private const val TTL_MILLIS = 30_000L
    private const val MAX_ENTRIES = 10_000

    private val logger = LoggerFactory.getLogger(UserStatusGate::class.java)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val userRepository: UserRepository by lazy { get(UserRepository::class.java) }

    private data class Entry(val allowed: Boolean, val expiresAt: Long)

    suspend fun isAllowed(email: String): Boolean {
        val key = email.lowercase().trim()
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { it.expiresAt > now }?.let { return it.allowed }

        return when (val result = userRepository.findUserByEmail(key)) {
            is Result.Success -> {
                val user = result.data
                val allowed = user != null && user.isActive && user.deletedAt == null
                if (cache.size >= MAX_ENTRIES) cache.clear()
                cache[key] = Entry(allowed, now + TTL_MILLIS)
                allowed
            }
            // Fail closed, and don't cache the failure so the next request retries.
            is Result.Error -> {
                logger.error("User status lookup failed for $key: ${result.message}")
                false
            }
        }
    }

    fun invalidate(email: String) {
        cache.remove(email.lowercase().trim())
    }
}
