package data.atlassian

import config.AtlassianOAuthConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlassianOAuthConfigTest {

    @Test
    fun `defaults match architect site cloudId and project`() {
        val config = AtlassianOAuthConfig.fromEnvironment(
            getSecret = {
                when (it) {
                    AtlassianOAuthConfig.SECRET_CLIENT_ID -> "cid"
                    AtlassianOAuthConfig.SECRET_CLIENT_SECRET -> "csec"
                    AtlassianOAuthConfig.SECRET_REDIRECT_URI -> "https://api.example.com/admin/atlassian/oauth/callback"
                    else -> ""
                }
            },
            getenv = { null },
        )
        assertEquals(AtlassianOAuthConfig.DEFAULT_BASE_URL, config.baseUrl)
        assertEquals(AtlassianOAuthConfig.DEFAULT_CLOUD_ID, config.cloudId)
        assertEquals(AtlassianOAuthConfig.DEFAULT_PROJECT, config.defaultProject)
        assertEquals(
            listOf("read:jira-work", "write:jira-work", "offline_access"),
            config.scopes,
        )
        assertTrue(config.isRedirectUriAllowed("https://api.example.com/admin/atlassian/oauth/callback"))
        assertTrue(config.isRedirectUriAllowed("http://localhost:8080/admin/atlassian/oauth/callback"))
        assertFalse(config.isRedirectUriAllowed("https://evil.example/callback"))
    }

    @Test
    fun `rejects non-allowlisted redirect when building auth url`() {
        val config = sampleConfig()
        val client = AtlassianOAuthClient(
            config = config,
            tokenStore = AtlassianTokenStore(),
            httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine) {
                engine { addHandler { error("unused") } }
            },
        )
        val result = client.buildAuthorizationUrl("https://evil.example/cb", "state")
        assertTrue(result is com.androidplay.core.common.Result.Error)
    }

    @Test
    fun `builds authorize url with required scopes`() {
        val config = sampleConfig()
        val client = AtlassianOAuthClient(
            config = config,
            tokenStore = AtlassianTokenStore(),
            httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine) {
                engine { addHandler { error("unused") } }
            },
        )
        val result = client.buildAuthorizationUrl(
            "https://api.example.com/admin/atlassian/oauth/callback",
            "abc",
        )
        assertTrue(result is com.androidplay.core.common.Result.Success)
        val url = (result as com.androidplay.core.common.Result.Success).data
        assertTrue(url.contains("client_id=cid"))
        assertTrue(url.contains("read"))
        assertTrue(url.contains("jira-work"))
        assertTrue(url.contains("offline_access"))
        assertTrue(url.contains("response_type=code"))
    }

    private fun sampleConfig() = AtlassianOAuthConfig(
        clientId = "cid",
        clientSecret = "csec",
        redirectUriAllowlist = setOf(
            "https://api.example.com/admin/atlassian/oauth/callback",
            "http://localhost:8080/admin/atlassian/oauth/callback",
        ),
        baseUrl = AtlassianOAuthConfig.DEFAULT_BASE_URL,
        cloudId = AtlassianOAuthConfig.DEFAULT_CLOUD_ID,
        defaultProject = AtlassianOAuthConfig.DEFAULT_PROJECT,
        tokenEndpoint = AtlassianOAuthConfig.DEFAULT_TOKEN_ENDPOINT,
        authEndpoint = AtlassianOAuthConfig.DEFAULT_AUTH_ENDPOINT,
        scopes = AtlassianOAuthConfig.DEFAULT_SCOPES,
    )
}
