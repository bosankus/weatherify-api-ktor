package data.atlassian

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * In-process single-use OAuth `state` store for admin bootstrap (APE-10).
 *
 * States expire after [ttlMs] and are consumed exactly once — missing, mismatched,
 * expired, or replayed values are rejected.
 */
class AtlassianOAuthStateStore(
    private val ttlMs: Long = 10 * 60 * 1000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()
    private val pending = linkedMapOf<String, Long>() // state -> expiresAtEpochMs

    suspend fun issue(): String {
        val state = UUID.randomUUID().toString()
        val expiresAt = clock() + ttlMs
        mutex.withLock {
            purgeExpiredLocked()
            pending[state] = expiresAt
        }
        return state
    }

    /**
     * Verifies and consumes [state]. Returns true only for a known, unexpired,
     * not-yet-consumed value. Always removes the entry on success (single-use).
     */
    suspend fun consume(state: String?): Boolean {
        if (state.isNullOrBlank()) return false
        return mutex.withLock {
            purgeExpiredLocked()
            val expiresAt = pending.remove(state) ?: return@withLock false
            expiresAt > clock()
        }
    }

    /** Test/observability helper — never expose state values in production logs. */
    suspend fun pendingCount(): Int = mutex.withLock {
        purgeExpiredLocked()
        pending.size
    }

    private fun purgeExpiredLocked() {
        val now = clock()
        val expired = pending.filterValues { it <= now }.keys
        expired.forEach { pending.remove(it) }
    }
}
