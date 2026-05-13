package com.transloom.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.transloom.repository.UserRepository
import com.androidplay.core.secrets.getSecretValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

fun Route.configureAuthRoutes(jwtSecret: String, userRepository: UserRepository) {
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }
    application.monitor.subscribe(ApplicationStopped) { httpClient.close() }

    val clientId = getSecretValue("github-client-id")
    val clientSecret = getSecretValue("github-client-secret")
    val redirectUri = getSecretValue("auth-callback-uri")
    val frontendRedirectUrl = getSecretValue("frontend-url")

    route("/transloom/auth") {

        get("/github") {
            val state = UUID.randomUUID().toString()
            call.response.cookies.append(
                Cookie(
                    name = "oauth_state",
                    value = state,
                    path = "/transloom/auth",
                    maxAge = 600,
                    httpOnly = true,
                    secure = getSecretValue("secure-cookies") != "false",
                    extensions = mapOf("SameSite" to "Lax")
                )
            )
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
            if (state == null || state != cookieState) {
                log.warn("OAuth state mismatch — possible CSRF attack (state={}, cookie={})", state, cookieState)
                call.respondText("Invalid OAuth state — authorization rejected", status = HttpStatusCode.Forbidden)
                return@get
            }
            call.response.cookies.append(
                Cookie("oauth_state", "", path = "/transloom/auth",
                    expires = io.ktor.util.date.GMTDate.START, maxAge = 0)
            )

            val code = call.request.queryParameters["code"]
            if (code == null) {
                call.respondText("Authorization failed: No code provided", status = HttpStatusCode.BadRequest)
                return@get
            }

            if (clientId == "dummy_client_id") {
                log.info("Mocking GitHub OAuth Exchange (no GITHUB_CLIENT_ID set)")
                val mockUser = userRepository.upsert(
                    githubId = 12345L,
                    username = "mock-developer",
                    email = "mock@transloom.dev",
                    avatarUrl = null,
                    githubToken = null
                )
                val mockToken = JWT.create()
                    .withAudience("transloom-app")
                    .withIssuer("transloom-backend")
                    .withClaim("userId", mockUser.id)
                    .withClaim("githubId", 12345L)
                    .withClaim("username", "mock-developer")
                    .withExpiresAt(Date(System.currentTimeMillis() + 604800000))
                    .sign(Algorithm.HMAC256(jwtSecret))
                call.respondRedirect(frontendRedirectUrl + mockToken)
                return@get
            }

            val tokenResponse: GitHubTokenResponse = httpClient.post("https://github.com/login/oauth/access_token") {
                header(HttpHeaders.Accept, "application/json")
                contentType(ContentType.Application.Json)
                setBody(GitHubTokenRequest(clientId, clientSecret, code, redirectUri))
            }.body()

            if (tokenResponse.access_token == null) {
                log.warn("GitHub OAuth token exchange failed: {}", tokenResponse.error_description)
                call.respondText(
                    "Failed to retrieve access token from GitHub: ${tokenResponse.error_description}",
                    status = HttpStatusCode.Unauthorized
                )
                return@get
            }

            val githubUser: GitHubUser = httpClient.get("https://api.github.com/user") {
                header(HttpHeaders.Authorization, "Bearer ${tokenResponse.access_token}")
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            }.body()

            val user = userRepository.upsert(
                githubId = githubUser.id,
                username = githubUser.login,
                email = githubUser.email,
                avatarUrl = githubUser.avatar_url,
                githubToken = tokenResponse.access_token
            )
            log.info("OAuth success: user={} id={}", githubUser.login, user.id)

            val jwtToken = JWT.create()
                .withAudience("transloom-app")
                .withIssuer("transloom-backend")
                .withClaim("userId", user.id)
                .withClaim("githubId", githubUser.id)
                .withClaim("username", githubUser.login)
                .withExpiresAt(Date(System.currentTimeMillis() + 604800000))
                .sign(Algorithm.HMAC256(jwtSecret))

            call.respondRedirect(frontendRedirectUrl + jwtToken)
        }
    }
}
