package data.atlassian

import com.androidplay.core.common.Result
import config.AtlassianOAuthConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Atlassian OAuth 2.0 (3LO) client for the shared service account.
 *
 * Owns authorization-URL building, code exchange, and single-flight refresh
 * with atomic refresh_token rotation. Access tokens stay inside middleware —
 * never returned to bot processes.
 *
 * APE-10
 */
class AtlassianOAuthClient(
    private val config: AtlassianOAuthConfig,
    private val tokenStore: AtlassianTokenStore,
    private val httpClient: HttpClient,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    private val log = LoggerFactory.getLogger(AtlassianOAuthClient::class.java)

    /** Single-flight lock so concurrent callers share one refresh. */
    private val refreshMutex = Mutex()

    fun buildAuthorizationUrl(redirectUri: String, state: String): Result<String> {
        if (!config.isRedirectUriAllowed(redirectUri)) {
            return Result.error("Redirect URI is not on the fixed allowlist")
        }
        val scope = config.scopes.joinToString(" ")
        val url = buildString {
            append(config.authEndpoint)
            append("?audience=").append(enc(config.audience))
            append("&client_id=").append(enc(config.clientId))
            append("&scope=").append(enc(scope))
            append("&redirect_uri=").append(enc(redirectUri))
            append("&state=").append(enc(state))
            append("&response_type=code")
            append("&prompt=consent")
        }
        return Result.success(url)
    }

    suspend fun exchangeAuthorizationCode(code: String, redirectUri: String): Result<Unit> {
        if (!config.isRedirectUriAllowed(redirectUri)) {
            return Result.error("Redirect URI is not on the fixed allowlist")
        }
        if (code.isBlank()) {
            return Result.error("Authorization code is blank")
        }
        return tokenRequest(
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "code" to code,
                "redirect_uri" to redirectUri,
            )
        ).map { Unit }
    }

    /**
     * Returns a valid access token for middleware use only.
     * Concurrent callers coalesce on a single refresh (mutex single-flight).
     */
    suspend fun getValidAccessToken(): Result<String> {
        if (tokenStore.isAccessTokenFresh(clock())) {
            val snap = tokenStore.snapshot()
            val token = snap.accessToken
            if (!token.isNullOrBlank()) return Result.success(token)
        }
        return refreshMutex.withLock {
            if (tokenStore.isAccessTokenFresh(clock())) {
                val token = tokenStore.snapshot().accessToken
                if (!token.isNullOrBlank()) return@withLock Result.success(token)
            }
            refreshAccessToken()
        }
    }

    /**
     * Force a refresh (e.g. after a classified 401 auth failure). Still single-flight.
     */
    suspend fun forceRefresh(): Result<String> = refreshMutex.withLock {
        tokenStore.clearAccessToken()
        refreshAccessToken()
    }

    private suspend fun refreshAccessToken(): Result<String> {
        val refresh = tokenStore.snapshot().refreshToken
        if (refresh.isNullOrBlank()) {
            return Result.error("Atlassian OAuth refresh token is not configured")
        }
        return tokenRequest(
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "refresh_token" to refresh,
            )
        ).map { it.accessToken }
    }

    private suspend fun tokenRequest(form: Map<String, String>): Result<TokenPayload> {
        return try {
            val response = httpClient.submitForm(
                url = config.tokenEndpoint,
                formParameters = Parameters.build {
                    form.forEach { (k, v) -> append(k, v) }
                },
            ) {
                header(HttpHeaders.Accept, "application/json")
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                val msg = AtlassianLogRedactor.safeError(
                    "Atlassian token endpoint error",
                    response.status.value,
                    classifyTokenError(body),
                )
                log.warn(msg)
                return Result.error(msg)
            }
            val parsed = json.decodeFromString(TokenResponse.serializer(), body)
            val access = parsed.accessToken
            if (access.isNullOrBlank()) {
                val msg = AtlassianLogRedactor.safeError(
                    "Atlassian token response missing access_token",
                    detail = parsed.errorDescription ?: parsed.error,
                )
                log.warn(msg)
                return Result.error(msg)
            }
            tokenStore.updateTokens(
                newAccessToken = access,
                newRefreshToken = parsed.refreshToken,
                expiresInSeconds = parsed.expiresIn ?: 3600L,
                nowEpochMs = clock(),
            )
            Result.success(
                TokenPayload(
                    accessToken = access,
                    refreshToken = parsed.refreshToken,
                    expiresIn = parsed.expiresIn ?: 3600L,
                )
            )
        } catch (e: Exception) {
            val msg = AtlassianLogRedactor.safeError(
                "Atlassian token request failed",
                detail = e.message,
            )
            log.error(msg)
            Result.error(msg, e)
        }
    }

    private fun classifyTokenError(body: String): String {
        return try {
            val parsed = json.decodeFromString(TokenResponse.serializer(), body)
            listOfNotNull(parsed.error, parsed.errorDescription).joinToString(" — ")
                .ifBlank { "token_error" }
        } catch (_: Exception) {
            "unparseable_token_error"
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("token_type") val tokenType: String? = null,
        val scope: String? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    private data class TokenPayload(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Long,
    )
}
