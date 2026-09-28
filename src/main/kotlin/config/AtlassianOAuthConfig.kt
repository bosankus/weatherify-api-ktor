package config

import com.androidplay.core.secrets.getSecretValue

/**
 * Single config object for Atlassian OAuth 2.0 (3LO) used by bot middleware.
 * Keeps site URL, cloudId, project key, and OAuth endpoints out of scattered magic strings.
 *
 * APE-10
 */
data class AtlassianOAuthConfig(
    val clientId: String,
    val clientSecret: String,
    /** Fixed allowlist: configured HTTPS redirect; localhost only if ATLASSIAN_OAUTH_LOCALHOST_REDIRECT is set. */
    val redirectUriAllowlist: Set<String>,
    val baseUrl: String,
    val cloudId: String,
    val defaultProject: String,
    val tokenEndpoint: String,
    val authEndpoint: String,
    val scopes: List<String>,
    val audience: String = "api.atlassian.com",
) {
    /** Jira Cloud REST base for this site: https://api.atlassian.com/ex/jira/{cloudId} */
    val jiraApiBaseUrl: String
        get() = "https://api.atlassian.com/ex/jira/$cloudId"

    fun isRedirectUriAllowed(redirectUri: String): Boolean =
        redirectUriAllowlist.any { it.equals(redirectUri.trim(), ignoreCase = false) }

    /**
     * APE-10 lock: bots may only touch the configured default project key (APE).
     * Comparison is case-insensitive after trim; path/query tricks are rejected.
     */
    fun isProjectKeyAllowed(key: String): Boolean {
        val normalized = key.trim().uppercase()
        if (normalized.isEmpty()) return false
        // Jira project keys: letters + digits; reject path / query tricks.
        if (!PROJECT_KEY_REGEX.matches(normalized)) return false
        return normalized == defaultProject.trim().uppercase()
    }

    fun isConfigured(): Boolean =
        clientId.isNotBlank() &&
            clientSecret.isNotBlank() &&
            clientId != "dummy_atlassian_client_id" &&
            clientSecret != "dummy_atlassian_client_secret"

    companion object {
        const val DEFAULT_BASE_URL = "https://androidplay.atlassian.net"
        const val DEFAULT_CLOUD_ID = "5dc5cb59-3451-414c-9239-406e9ae97a96"
        const val DEFAULT_PROJECT = "APE"
        const val DEFAULT_TOKEN_ENDPOINT = "https://auth.atlassian.com/oauth/token"
        const val DEFAULT_AUTH_ENDPOINT = "https://auth.atlassian.com/authorize"
        val DEFAULT_SCOPES = listOf("read:jira-work", "write:jira-work", "offline_access")
        private val PROJECT_KEY_REGEX = Regex("^[A-Za-z][A-Za-z0-9]{1,9}$")

        const val SECRET_CLIENT_ID = "atlassian-oauth-client-id"
        const val SECRET_CLIENT_SECRET = "atlassian-oauth-client-secret"
        const val SECRET_REDIRECT_URI = "atlassian-oauth-redirect-uri"
        const val SECRET_REFRESH_TOKEN = "atlassian-oauth-refresh-token"
        const val SECRET_BOT_SHARED = "bot-atlassian-shared-secret"

        fun fromEnvironment(
            getSecret: (String) -> String = ::getSecretValue,
            getenv: (String) -> String? = { System.getenv(it) },
        ): AtlassianOAuthConfig {
            val configuredRedirect = getSecret(SECRET_REDIRECT_URI).trim()
            val allowlist = buildSet {
                if (configuredRedirect.isNotBlank()) add(configuredRedirect)
                // Localhost bootstrap ONLY when explicitly set — never auto-added in prod.
                getenv("ATLASSIAN_OAUTH_LOCALHOST_REDIRECT")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(it) }
            }

            return AtlassianOAuthConfig(
                clientId = getSecret(SECRET_CLIENT_ID),
                clientSecret = getSecret(SECRET_CLIENT_SECRET),
                redirectUriAllowlist = allowlist,
                baseUrl = getenv("ATLASSIAN_BASE_URL")?.trim()?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_BASE_URL,
                cloudId = getenv("ATLASSIAN_CLOUD_ID")?.trim()?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_CLOUD_ID,
                defaultProject = getenv("ATLASSIAN_DEFAULT_PROJECT")?.trim()?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_PROJECT,
                tokenEndpoint = getenv("ATLASSIAN_TOKEN_ENDPOINT")?.trim()?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_TOKEN_ENDPOINT,
                authEndpoint = getenv("ATLASSIAN_AUTH_ENDPOINT")?.trim()?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_AUTH_ENDPOINT,
                scopes = DEFAULT_SCOPES,
            )
        }
    }
}
