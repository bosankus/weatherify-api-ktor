package data.atlassian

import com.androidplay.core.common.Result
import config.AtlassianOAuthConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlassianOAuthClientTest {

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
    fun `happy path refresh rotates tokens atomically and does not leak secrets in errors`() = runBlocking {
        var refreshCalls = 0
        val engine = MockEngine { request ->
            refreshCalls++
            // Token endpoint must be hit for refresh; body content is internal to middleware.
            assertTrue(request.url.toString().contains("/oauth/token"))
            respond(
                content = """{"access_token":"access-NEW","refresh_token":"refresh-NEW","expires_in":3600,"token_type":"Bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val clock = { 1_000_000L }
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD", clock = clock)
        val oauth = AtlassianOAuthClient(config(), store, client, clock = clock)

        val result = oauth.getValidAccessToken()
        assertTrue(result is Result.Success)
        assertEquals("access-NEW", (result as Result.Success).data)
        assertEquals(1, refreshCalls)

        val health = store.health()
        assertTrue(health.hasRefreshToken)
        assertTrue(health.accessTokenFresh)
        // health payload must not expose token strings
        val healthStr = health.toString()
        assertFalse(healthStr.contains("access-NEW"))
        assertFalse(healthStr.contains("refresh-NEW"))
        assertFalse(healthStr.contains("refresh-OLD"))

        // Cached token reused without second refresh
        val again = oauth.getValidAccessToken()
        assertTrue(again is Result.Success)
        assertEquals(1, refreshCalls)
        client.close()
    }

    @Test
    fun `failure path returns redacted error without token leak`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_grant","error_description":"refresh_token=rt_SHOULD_NOT_LEAK"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD")
        val oauth = AtlassianOAuthClient(config(), store, client)

        val result = oauth.getValidAccessToken()
        assertTrue(result is Result.Error)
        val message = (result as Result.Error).message
        assertFalse(message.contains("rt_SHOULD_NOT_LEAK"))
        assertFalse(message.contains("refresh-OLD"))
        assertFalse(message.contains("csec"))
        assertTrue(AtlassianLogRedactor.redact(message) == message || !message.contains("Bearer "))
        client.close()
    }

    @Test
    fun `single-flight refresh under concurrency`() = runBlocking {
        var refreshCalls = 0
        val engine = MockEngine {
            refreshCalls++
            kotlinx.coroutines.delay(50)
            respond(
                content = """{"access_token":"access-SF","refresh_token":"refresh-SF","expires_in":3600}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val clock = { 1_000_000L }
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD", clock = clock)
        val oauth = AtlassianOAuthClient(config(), store, client, clock = clock)

        val results = kotlinx.coroutines.coroutineScope {
            (1..8).map {
                async { oauth.getValidAccessToken() }
            }.map { it.await() }
        }
        assertTrue(results.all { it is Result.Success })
        assertEquals(1, refreshCalls)
        client.close()
    }

    @Test
    fun `code exchange rejects non-allowlisted redirect`() = runBlocking {
        val client = HttpClient(MockEngine { error("should not be called") })
        val oauth = AtlassianOAuthClient(config(), AtlassianTokenStore(), client)
        val result = oauth.exchangeAuthorizationCode("code", "https://evil.example/cb")
        assertTrue(result is Result.Error)
        client.close()
    }


    @Test
    fun `missing refresh token fails without calling token endpoint`() = runBlocking {
        var refreshCalls = 0
        val engine = MockEngine {
            refreshCalls++
            error("token endpoint must not be called when refresh token is missing")
        }
        val client = HttpClient(engine)
        val oauth = AtlassianOAuthClient(config(), AtlassianTokenStore(initialRefreshToken = null), client)

        val result = oauth.getValidAccessToken()
        assertTrue(result is Result.Error)
        val message = (result as Result.Error).message
        assertTrue(message.contains("refresh token is not configured", ignoreCase = true))
        assertEquals(0, refreshCalls)
        assertFalse(message.contains("csec"))
        client.close()
    }

    @Test
    fun `blank refresh token fails without calling token endpoint`() = runBlocking {
        val engine = MockEngine { error("should not be called") }
        val client = HttpClient(engine)
        val oauth = AtlassianOAuthClient(config(), AtlassianTokenStore(initialRefreshToken = "   "), client)

        val result = oauth.getValidAccessToken()
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message.contains("refresh token is not configured", ignoreCase = true))
        client.close()
    }

    @Test
    fun `invalid refresh token invalid_grant is redacted and does not leak secrets`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_grant","error_description":"The refresh token is invalid: refresh_token=rt_INVALID_LEAK"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-BAD")
        val oauth = AtlassianOAuthClient(config(), store, client)

        val result = oauth.getValidAccessToken()
        assertTrue(result is Result.Error)
        val message = (result as Result.Error).message
        assertTrue(message.contains("invalid_grant") || message.contains("token endpoint", ignoreCase = true))
        assertFalse(message.contains("rt_INVALID_LEAK"))
        assertFalse(message.contains("refresh-BAD"))
        assertFalse(message.contains("client_secret=csec"))
        assertFalse(message.contains("refresh_token=rt_"))
        client.close()
    }

    @Test
    fun `revoked client invalid_client fails without leaking client secret`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_client","error_description":"Client authentication failed for client_secret=csec_SHOULD_NOT_APPEAR"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val store = AtlassianTokenStore(initialRefreshToken = "refresh-OLD")
        val oauth = AtlassianOAuthClient(config(), store, client)

        val result = oauth.getValidAccessToken()
        assertTrue(result is Result.Error)
        val message = (result as Result.Error).message
        assertTrue(message.contains("invalid_client") || message.contains("token endpoint", ignoreCase = true))
        assertFalse(message.contains("csec_SHOULD_NOT_APPEAR"))
        assertFalse(message.contains("refresh-OLD"))
        assertFalse(message.contains("client_secret=csec"))
        assertTrue(message.contains("[REDACTED]") || !message.contains("client_secret="))
        client.close()
    }
}
