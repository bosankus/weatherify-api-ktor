package data.atlassian

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the shared Atlassian service-account OAuth tokens.
 *
 * Updates are atomic under a mutex so concurrent refresh_token rotation cannot
 * race and invalidate tokens. Production seeds the refresh token from secret
 * `atlassian-oauth-refresh-token` / env `ATLASSIAN_OAUTH_REFRESH_TOKEN`.
 *
 * APE-10
 */
class AtlassianTokenStore(
    initialRefreshToken: String? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    @Volatile
    private var refreshToken: String? = initialRefreshToken?.takeIf { it.isNotBlank() }

    @Volatile
    private var accessToken: String? = null

    /** Epoch millis when the access token is considered expired (already includes skew). */
    @Volatile
    private var accessExpiresAtEpochMs: Long = 0L

    data class Snapshot(
        val accessToken: String?,
        val refreshToken: String?,
        val accessExpiresAtEpochMs: Long,
    )

    suspend fun snapshot(): Snapshot = mutex.withLock {
        Snapshot(accessToken, refreshToken, accessExpiresAtEpochMs)
    }

    fun hasRefreshToken(): Boolean = !refreshToken.isNullOrBlank()

    fun isAccessTokenFresh(nowEpochMs: Long = clock()): Boolean {
        val token = accessToken
        return !token.isNullOrBlank() && nowEpochMs < accessExpiresAtEpochMs
    }

    /**
     * Atomically rotate tokens. When [newRefreshToken] is null/blank the previous
     * refresh token is retained (Atlassian may omit it on refresh).
     */
    suspend fun updateTokens(
        newAccessToken: String,
        newRefreshToken: String?,
        expiresInSeconds: Long,
        nowEpochMs: Long = clock(),
        skewMs: Long = 60_000L,
    ) {
        require(newAccessToken.isNotBlank()) { "access token must not be blank" }
        mutex.withLock {
            accessToken = newAccessToken
            if (!newRefreshToken.isNullOrBlank()) {
                refreshToken = newRefreshToken
            }
            accessExpiresAtEpochMs = nowEpochMs + (expiresInSeconds * 1000L) - skewMs
        }
    }

    suspend fun clearAccessToken() = mutex.withLock {
        accessToken = null
        accessExpiresAtEpochMs = 0L
    }

    /** Health-safe view — never exposes token values. */
    suspend fun health(): TokenHealth {
        val snap = snapshot()
        return TokenHealth(
            hasRefreshToken = !snap.refreshToken.isNullOrBlank(),
            hasAccessToken = !snap.accessToken.isNullOrBlank(),
            accessTokenFresh = isAccessTokenFresh(),
            accessExpiresAtEpochMs = if (snap.accessToken.isNullOrBlank()) null else snap.accessExpiresAtEpochMs,
        )
    }
}

data class TokenHealth(
    val hasRefreshToken: Boolean,
    val hasAccessToken: Boolean,
    val accessTokenFresh: Boolean,
    val accessExpiresAtEpochMs: Long?,
)
