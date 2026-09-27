package data.atlassian

import com.androidplay.core.common.Result
import config.AtlassianOAuthConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Authenticated Atlassian / Jira REST client used exclusively by middleware.
 *
 * Bots must call backend `/bot/...` APIs — this client never returns access tokens.
 * Applies exponential backoff on 429/5xx and refreshes only on classified auth failures.
 *
 * APE-10
 */
class AtlassianApiClient(
    private val config: AtlassianOAuthConfig,
    private val oauthClient: AtlassianOAuthClient,
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val maxAttempts: Int = 4,
    private val initialBackoffMs: Long = 200L,
) {
    private val log = LoggerFactory.getLogger(AtlassianApiClient::class.java)

    suspend fun getMyself(): Result<JsonObject> =
        getJson("/rest/api/3/myself")

    suspend fun getProject(key: String = config.defaultProject): Result<JsonObject> {
        val normalized = key.trim().uppercase()
        if (!config.isProjectKeyAllowed(normalized)) {
            return Result.error("Project key is not allowed (APE-10 constraint)")
        }
        return getJson("/rest/api/3/project/$normalized")
    }

    suspend fun getJson(path: String): Result<JsonObject> =
        execute(HttpMethod.Get, path).map { body ->
            json.parseToJsonElement(body).jsonObject
        }

    suspend fun execute(
        method: HttpMethod,
        path: String,
        body: String? = null,
    ): Result<String> {
        var attempt = 0
        var backoff = initialBackoffMs
        var refreshedForAuth = false

        while (true) {
            attempt++
            val tokenResult = oauthClient.getValidAccessToken()
            val accessToken = when (tokenResult) {
                is Result.Success -> tokenResult.data
                is Result.Error -> return tokenResult
            }

            val url = resolveUrl(path)
            try {
                val response = httpClient.request(url) {
                    this.method = method
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    header(HttpHeaders.Accept, "application/json")
                    if (body != null) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                }
                val status = response.status
                val responseBody = response.bodyAsText()

                if (status.isSuccess()) {
                    return Result.success(responseBody)
                }

                if (isClassifiedAuthFailure(status, responseBody) && !refreshedForAuth) {
                    refreshedForAuth = true
                    log.info("Classified Atlassian auth failure (401) — forcing token refresh")
                    when (val refresh = oauthClient.forceRefresh()) {
                        is Result.Error -> return refresh
                        is Result.Success -> { /* retry with new token */ }
                    }
                    continue
                }

                if ((status.value == 429 || status.value in 500..599) && attempt < maxAttempts) {
                    val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
                    val waitMs = ((retryAfter ?: 0L) * 1000L).coerceAtLeast(backoff)
                    log.warn(
                        AtlassianLogRedactor.safeError(
                            "Atlassian API transient failure; backing off",
                            status.value,
                            "attempt=$attempt waitMs=$waitMs",
                        )
                    )
                    delay(waitMs)
                    backoff = (backoff * 2).coerceAtMost(5_000L)
                    continue
                }

                val msg = AtlassianLogRedactor.safeError(
                    "Atlassian API request failed",
                    status.value,
                    summarizeErrorBody(responseBody),
                )
                log.warn(msg)
                return Result.error(msg)
            } catch (e: Exception) {
                if (attempt < maxAttempts) {
                    log.warn(
                        AtlassianLogRedactor.safeError(
                            "Atlassian API transport error; backing off",
                            detail = "attempt=$attempt ${e.message}",
                        )
                    )
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(5_000L)
                    continue
                }
                val msg = AtlassianLogRedactor.safeError(
                    "Atlassian API request failed",
                    detail = e.message,
                )
                log.error(msg)
                return Result.error(msg, e)
            }
        }
    }

    private fun resolveUrl(path: String): String {
        // Defense in depth: never follow absolute URLs (SSRF footgun if path is ever caller-influenced).
        require(!path.startsWith("http://") && !path.startsWith("https://")) {
            "Absolute Atlassian request URLs are not allowed"
        }
        require(!path.contains("..")) { "Path traversal is not allowed" }
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "${config.jiraApiBaseUrl}$normalized"
    }

    /**
     * Refresh only on classified auth failures: HTTP 401 plus an auth-shaped body
     * (or empty body). Plain 401s that look like permission/resource errors are not
     * treated as token refresh triggers.
     */
    internal fun isClassifiedAuthFailure(status: HttpStatusCode, body: String): Boolean {
        if (status != HttpStatusCode.Unauthorized) return false
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return true
        val lower = trimmed.lowercase()
        val authMarkers = listOf(
            "unauthorized",
            "authentication",
            "authenticate",
            "invalid token",
            "expired",
            "oauth",
            "www-authenticate",
            "\"errorcode\":401",
            "as_unauthorized",
        )
        return authMarkers.any { lower.contains(it) }
    }

    private fun summarizeErrorBody(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val message = obj["message"]?.jsonPrimitive?.content
            val errorMessages = obj["errorMessages"]?.toString()
            val error = obj["error"]?.jsonPrimitive?.content
            listOfNotNull(message, error, errorMessages)
                .joinToString(" ")
                .ifBlank { "atlassian_error" }
                .let { AtlassianLogRedactor.redact(it) }
                .take(200)
        } catch (_: Exception) {
            "unparseable_error_body"
        }
    }
}
