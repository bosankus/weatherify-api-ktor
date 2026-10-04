package config

/**
 * Session rules that do not touch Mongo or JWT libraries, so they can be unit tested.
 *
 * A missing stored generation is 0. A token with no generation claim is a legacy token:
 * authenticated calls stay allowed until the JWT expires, and refresh is allowed only
 * while the access token is still within [util.Constants.Auth] expiration. An expired
 * legacy token is not refreshed. A token that carries a generation is accepted only
 * when that integer matches the user document.
 */
enum class SessionPurpose {
    AUTHENTICATED_CALL,
    REFRESH,
}

enum class SessionDecision {
    ALLOW,
    REJECT_MISSING_USER,
    REJECT_INACTIVE,
    REJECT_GENERATION_MISMATCH,
    REJECT_LEGACY_EXPIRED_REFRESH,
}

object SessionPolicy {
    /** First login after this field exists writes 0. A later login increments. */
    fun generationForLogin(stored: Int?): Int = if (stored == null) 0 else stored + 1

    /** Logout always moves the generation forward, including when the field was missing. */
    fun generationForLogout(stored: Int?): Int = (stored ?: 0) + 1

    /** Value compared to a token claim. Missing document field matches claim 0. */
    fun effectiveGeneration(stored: Int?): Int = stored ?: 0

    fun evaluate(
        userPresent: Boolean,
        isActive: Boolean,
        storedGeneration: Int?,
        tokenGeneration: Int?,
        purpose: SessionPurpose,
        tokenExpired: Boolean,
    ): SessionDecision {
        if (!userPresent) return SessionDecision.REJECT_MISSING_USER
        if (!isActive) return SessionDecision.REJECT_INACTIVE
        if (tokenGeneration == null) {
            if (purpose == SessionPurpose.REFRESH && tokenExpired) {
                return SessionDecision.REJECT_LEGACY_EXPIRED_REFRESH
            }
            return SessionDecision.ALLOW
        }
        if (tokenGeneration != effectiveGeneration(storedGeneration)) {
            return SessionDecision.REJECT_GENERATION_MISMATCH
        }
        return SessionDecision.ALLOW
    }
}
