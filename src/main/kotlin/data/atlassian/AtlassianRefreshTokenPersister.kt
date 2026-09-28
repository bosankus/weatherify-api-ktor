package data.atlassian

import com.androidplay.core.secrets.updateSecretValue
import config.AtlassianOAuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Persists rotated Atlassian refresh tokens to GCP Secret Manager (APE-10).
 *
 * Never logs the token value. Failures are reported to the caller so health can
 * surface a flag without failing the in-memory token update / request.
 *
 * Suspends and runs Secret Manager HTTP on [Dispatchers.IO] so callers can
 * release [data.atlassian.AtlassianOAuthClient]'s refresh mutex before awaiting
 * the durable write.
 */
class AtlassianRefreshTokenPersister(
    private val secretName: String = AtlassianOAuthConfig.SECRET_REFRESH_TOKEN,
    private val update: (secretName: String, value: String) -> Boolean = ::updateSecretValue,
) {
    private val log = LoggerFactory.getLogger(AtlassianRefreshTokenPersister::class.java)

    /**
     * @return true if Secret Manager acknowledged a new version; false on any failure.
     */
    suspend fun persistRotatedRefreshToken(newRefreshToken: String): Boolean {
        if (newRefreshToken.isBlank()) {
            log.warn("Skipping SM persist: blank refresh token")
            return false
        }
        return try {
            val ok = withContext(Dispatchers.IO) {
                update(secretName, newRefreshToken)
            }
            if (ok) {
                log.info(
                    "Persisted rotated Atlassian refresh token to Secret Manager key '{}' (value redacted)",
                    secretName,
                )
            } else {
                log.error(
                    "Failed to persist rotated Atlassian refresh token to Secret Manager key '{}' — " +
                        "in-memory token is updated but durable store may be stale until next successful persist",
                    secretName,
                )
            }
            ok
        } catch (e: Exception) {
            log.error(
                "Exception persisting rotated Atlassian refresh token to '{}': {} (value redacted)",
                secretName,
                e.toString(),
            )
            false
        }
    }
}
