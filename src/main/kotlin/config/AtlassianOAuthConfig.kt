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
    /** Fixed allowlist: configured HTTPS redirect + optional localhost for bootstrap only. */
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

        const val SECRET_CLIENT_ID = "atlassian-oauth-client-id"
        const val SECRET_CLIENT_SECRET = "atlassian-oauth-client-secret"
        const val SECRET_REDIRECT_URI = "atlassian-oauth-redirect-uri"
        const val SECRET_REFRESH_TOKEN = "atlassian-oauth-refresh-token"

        fun fromEnvironment(
            getSecret: (String) -> String = ::getSecretValue,
            getenv: (String) -> String? = { System.getenv(it) },
        ): AtlassianOAuthConfig {
            val configuredRedirect = getSecret(SECRET_REDIRECT_URI).trim()
            val allowlist = buildSet {
                if (configuredRedirect.isNotBlank()) add(configuredRedirect)
                // Optional localhost bootstrap only (never production callback).
                getenv("ATLASSIAN_OAUTH_LOCALHOST_REDIRECT")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(it) }
                    ?: run {
                        // Sensible default localhost bootstrap URI when none configured.
                        add("http://localhost:8080/admin/atlassian/oauth/callback")
                    }
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
