package data.atlassian

import com.androidplay.core.common.Result
import config.AtlassianOAuthConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlassianApiClientTest {

    private fun config() = AtlassianOAuthConfig(
        clientId = "cid",
        clientSecret = "csec",
        redirectUriAllowlist = setOf("https://api.example.com/admin/atlassian/oauth/callback"),
        baseUrl = AtlassianOAuthConfig.DEFAULT_BASE_URL,
        cloudId = AtlassianOAuthConfig.DEFAULT_CLOUD_ID,
        defaultProject = AtlassianOAuthConfig.DEFAULT_PROJECT,
        tokenEndpoint = "https://auth.atlassian.com/oauth/token",
        authEndpoint = AtlassianOAuthConfig.DEFAULT_AUTH_ENDPOINT,
        scopes = AtlassianOAuthConfig.DEFAULT_SCOPES,
    )

    @Test
    fun `happy path myself proxies without exposing token`() = runBlocking {
        var sawAuthHeader = false
        val engine = MockEngine { request ->
            when {
                request.url.toString().contains("/oauth/token") -> respond(
                    """{"access_token":"access-XYZ","refresh_token":"refresh-XYZ","expires_in":3600}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                request.url.toString().contains("/myself") -> {
                    val auth = request.headers[HttpHeaders.Authorization]
                    sawAuthHeader = auth == "Bearer access-XYZ"
                    respond(
                        """{"accountId":"abc","displayName":"Bot"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond("unexpected", HttpStatusCode.NotFound)
            }
        }
        val http = HttpClient(engine)
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD", clock = { 1_000_000L })
        val oauth = AtlassianOAuthClient(config(), store, http, clock = { 1_000_000L })
        val api = AtlassianApiClient(config(), oauth, http, maxAttempts = 2, initialBackoffMs = 1)

        val result = api.getMyself()
        assertTrue(result is Result.Success)
        assertTrue(sawAuthHeader)
        val data = (result as Result.Success).data
        assertEquals("Bot", data["displayName"]?.toString()?.trim('"'))
        // Result must not contain the access token
        assertFalse(result.toString().contains("access-XYZ"))
        http.close()
    }

    @Test
    fun `classified 401 triggers single refresh then succeeds`() = runBlocking {
        var tokenCalls = 0
        var myselfCalls = 0
        val engine = MockEngine { request ->
            when {
                request.url.toString().contains("/oauth/token") -> {
                    tokenCalls++
                    respond(
                        """{"access_token":"access-$tokenCalls","refresh_token":"refresh-$tokenCalls","expires_in":3600}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                request.url.toString().contains("/myself") -> {
                    myselfCalls++
                    if (myselfCalls == 1) {
                        respond(
                            """{"message":"Unauthorized - authentication failed"}""",
                            HttpStatusCode.Unauthorized,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            """{"accountId":"abc","displayName":"Bot"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
                else -> respond("unexpected", HttpStatusCode.NotFound)
            }
        }
        val http = HttpClient(engine)
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD", clock = { 1_000_000L })
        val oauth = AtlassianOAuthClient(config(), store, http, clock = { 1_000_000L })
        val api = AtlassianApiClient(config(), oauth, http, maxAttempts = 3, initialBackoffMs = 1)

        val result = api.getMyself()
        assertTrue(result is Result.Success)
        assertEquals(2, tokenCalls) // initial + force refresh
        assertEquals(2, myselfCalls)
        http.close()
    }

    @Test
    fun `non-auth 401 does not blindly refresh`() = runBlocking {
        var tokenCalls = 0
        val engine = MockEngine { request ->
            when {
                request.url.toString().contains("/oauth/token") -> {
                    tokenCalls++
                    respond(
                        """{"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond(
                    // 401 but not auth-shaped (e.g. resource/permission wording without auth markers)
                    """{"errorMessages":["Issue does not exist or you do not have permission to see it."]}""",
                    HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val http = HttpClient(engine)
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD", clock = { 1_000_000L })
        val oauth = AtlassianOAuthClient(config(), store, http, clock = { 1_000_000L })
        val api = AtlassianApiClient(config(), oauth, http, maxAttempts = 2, initialBackoffMs = 1)

        // "permission" alone shouldn't match — but "Unauthorized" status with body without auth markers:
        // Our classifier looks for auth markers in body; this body has none of the auth markers
        // wait - "you do not have permission" doesn't include auth markers. Good.
        val result = api.getMyself()
        assertTrue(result is Result.Error)
        assertEquals(1, tokenCalls) // only the initial token fetch, no force refresh
        val message = (result as Result.Error).message
        assertFalse(message.contains("access-1"))
        assertFalse(message.contains("refresh-1"))
        http.close()
    }

    @Test
    fun `backoff retries on 503 then succeeds`() = runBlocking {
        var myselfCalls = 0
        val engine = MockEngine { request ->
            when {
                request.url.toString().contains("/oauth/token") -> respond(
                    """{"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> {
                    myselfCalls++
                    if (myselfCalls < 3) {
                        respond("unavailable", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(
                            """{"key":"APE","name":"Androidplay Agentic Engineering"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            }
        }
        val http = HttpClient(engine)
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD", clock = { 1_000_000L })
        val oauth = AtlassianOAuthClient(config(), store, http, clock = { 1_000_000L })
        val api = AtlassianApiClient(config(), oauth, http, maxAttempts = 4, initialBackoffMs = 1)

        val result = api.getProject("APE")
        assertTrue(result is Result.Success)
        assertEquals(3, myselfCalls)
        http.close()
    }

    @Test
    fun `rejects non-APE project key without calling Atlassian`() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls++
            error("Atlassian must not be called for disallowed project keys")
        }
        val http = HttpClient(engine)
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD", clock = { 1_000_000L })
        val oauth = AtlassianOAuthClient(config(), store, http, clock = { 1_000_000L })
        val api = AtlassianApiClient(config(), oauth, http, maxAttempts = 2, initialBackoffMs = 1)

        val result = api.getProject("EVIL")
        assertTrue(result is Result.Error)
        assertEquals(0, calls)
        assertTrue((result as Result.Error).message.contains("not allowed", ignoreCase = true))
        http.close()
    }
}
