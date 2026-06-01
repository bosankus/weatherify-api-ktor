package com.syncling.routes

import com.syncling.domain.UserEvent
import com.syncling.repository.UserRepository
import com.syncling.services.UserActivityService
import com.androidplay.core.secrets.getSecretValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger("AuthRoutes")

@Serializable
data class GitHubTokenRequest(
    val client_id: String,
    val client_secret: String,
    val code: String,
    val redirect_uri: String
)

@Serializable
data class GitHubTokenResponse(
    val access_token: String? = null,
    val error: String? = null,
    val error_description: String? = null
)

@Serializable
data class GitHubUser(
    val id: Long,
    val login: String,
    val email: String? = null,
    val avatar_url: String? = null
)

fun Route.configureAuthRoutes(
    jwtSecret: String,
    userRepository: UserRepository,
    userActivityService: UserActivityService
) {
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    application.monitor.subscribe(ApplicationStopped) { httpClient.close() }

    val clientId = getSecretValue("github-client-id")
    val clientSecret = getSecretValue("github-client-secret")
    val redirectUri = "https://syncling.space/auth/github/callback"
    val frontendRedirectUrl = getSecretValue("frontend-url")

    route("/auth") {

        get("/github") {
            val state = UUID.randomUUID().toString()
            call.response.cookies.append(Cookie(
                name = "oauth_state", value = state,
                path = "/", maxAge = 600,
                httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
            ))
            val url = "https://github.com/login/oauth/authorize" +
                    "?client_id=$clientId" +
                    "&redirect_uri=$redirectUri" +
                    "&scope=user:email,repo" +
                    "&state=$state"
            call.respondRedirect(url)
        }

        get("/github/callback") {
            val state = call.request.queryParameters["state"]
            val cookieState = call.request.cookies["oauth_state"]
            if (cookieState == null) {
                // Cookie missing — session likely expired or user opened a stale link.
                log.warn("OAuth state cookie missing — redirecting to login (state={})", state)
                call.respondRedirect("/auth/github")
                return@get
            }
            if (state == null || state != cookieState) {
                log.warn("OAuth state mismatch — possible CSRF (state={}, cookie={})", state, cookieState)
                call.respondText("Invalid OAuth state — authorization rejected", status = HttpStatusCode.Forbidden)
                return@get
            }
            call.response.cookies.append(Cookie("oauth_state", "", path = "/",
                expires = GMTDate.START, maxAge = 0))

            // Pull the plan the user picked on the pricing page (if any) so we can route them
            // back into the branded checkout flow once they're signed in.
            val pendingPlan = call.request.cookies[PENDING_PLAN_COOKIE]
            if (!pendingPlan.isNullOrBlank()) {
                call.response.cookies.append(Cookie(PENDING_PLAN_COOKIE, "", path = "/",
                    expires = GMTDate.START, maxAge = 0))
            }

            val code = call.request.queryParameters["code"]
            if (code == null) {
                call.respondText("Authorization failed: No code provided", status = HttpStatusCode.BadRequest)
                return@get
            }

            val isDevMode = System.getenv("APP_ENV")?.lowercase() != "production"
            if (isDevMode && clientId == "dummy_client_id") {
                log.info("Mocking GitHub OAuth (no GITHUB_CLIENT_ID set — dev mode only)")
                val mockResult = userRepository.upsert(12345L, "mock-developer", "mock@syncling.dev", null, null)
                val mockUser = mockResult.user
                call.application.launch {
                    userActivityService.record(
                        mockUser.id,
                        if (mockResult.isNewUser) UserEvent.SIGNED_UP else UserEvent.LOGGED_IN,
                        mapOf("flow" to "mock")
                    )
                }
                val mockToken = mintJwt(jwtSecret, mockUser.id, 12345L, "mock-developer")
                call.issueSessionCookie(mockToken)
                redirectAfterAuth(call, mockToken, pendingPlan, frontendRedirectUrl, mockResult.isNewUser)
                return@get
            }

            val tokenResponse: GitHubTokenResponse = httpClient.post("https://github.com/login/oauth/access_token") {
                header(HttpHeaders.Accept, "application/json")
                contentType(ContentType.Application.Json)
                setBody(GitHubTokenRequest(clientId, clientSecret, code, redirectUri))
            }.body()

            if (tokenResponse.access_token == null) {
                log.warn("GitHub token exchange failed: {}", tokenResponse.error_description)
                call.respondText("Authorization failed", status = HttpStatusCode.Unauthorized)
                return@get
            }

            val githubUser: GitHubUser = httpClient.get("https://api.github.com/user") {
                header(HttpHeaders.Authorization, "Bearer ${tokenResponse.access_token}")
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            }.body()

            val upsertResult = userRepository.upsert(
                githubId = githubUser.id, username = githubUser.login,
                email = githubUser.email, avatarUrl = githubUser.avatar_url,
                githubToken = tokenResponse.access_token
            )
            val user = upsertResult.user
            log.info("OAuth success: user={} id={} new={}", githubUser.login, user.id, upsertResult.isNewUser)

            call.application.launch {
                userActivityService.record(
                    user.id,
                    if (upsertResult.isNewUser) UserEvent.SIGNED_UP else UserEvent.LOGGED_IN,
                    mapOf(
                        "githubLogin" to githubUser.login,
                        "pendingPlan" to (pendingPlan ?: "")
                    )
                )
            }

            val jwtToken = mintJwt(jwtSecret, user.id, githubUser.id, githubUser.login)
            call.issueSessionCookie(jwtToken)
            redirectAfterAuth(call, jwtToken, pendingPlan, frontendRedirectUrl, upsertResult.isNewUser)
        }

        get("/logout") {
            call.clearSession()
            call.respondRedirect("/syncling")
        }
    }
}

private suspend fun redirectAfterAuth(
    call: ApplicationCall,
    jwtToken: String,
    pendingPlan: String?,
    frontendRedirectUrl: String,
    isNewUser: Boolean
) {
    when {
        !pendingPlan.isNullOrBlank() -> call.respondRedirect("/billing/checkout?plan=${pendingPlan.uppercase()}")
        isNewUser -> call.respondRedirect("/welcome")
        else -> {
            // Session cookie (httpOnly) is already set. Use a short-lived JS-readable bootstrap
            // cookie instead of putting the JWT in the URL — keeps the token out of proxy logs,
            // browser history, and Referer headers.
            call.response.cookies.append(Cookie(
                name = "tl_token_bootstrap", value = jwtToken,
                path = "/", maxAge = 15,
                httpOnly = false, secure = true, extensions = mapOf("SameSite" to "Lax")
            ))
            call.respondRedirect(frontendRedirectUrl)
        }
    }
}

private fun ApplicationCall.issueSessionCookie(token: String) {
    response.cookies.append(Cookie(
        name = SESSION_COOKIE, value = token,
        path = "/", maxAge = (JWT_TTL_MS / 1000).toInt(),
        httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
    ))
}
