package com.transloom.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.transloom.repository.UserRepository
import com.transloom.services.RazorpayBillingService
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
    razorpayService: RazorpayBillingService
) {
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    application.monitor.subscribe(ApplicationStopped) { httpClient.close() }

    val clientId = getSecretValue("github-client-id")
    val clientSecret = getSecretValue("github-client-secret")
    val redirectUri = "https://data.androidplay.in/transloom/auth/github/callback"
    val frontendRedirectUrl = getSecretValue("frontend-url")

    route("/transloom/auth") {

        get("/github") {
            val state = UUID.randomUUID().toString()
            call.response.cookies.append(Cookie(
                name = "oauth_state", value = state,
                path = "/transloom/auth", maxAge = 600,
                httpOnly = true, secure = true, extensions = mapOf("SameSite" to "Lax")
            ))
            // rp_subscription / rp_plan cookies are already set by /transloom/billing/rp-callback
            // before this route is called in the anonymous payment-first flow. Nothing to add here.
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
                log.warn("OAuth state mismatch — possible CSRF (state={}, cookie={})", state, cookieState)
                call.respondText("Invalid OAuth state — authorization rejected", status = HttpStatusCode.Forbidden)
                return@get
            }
            call.response.cookies.append(Cookie("oauth_state", "", path = "/transloom/auth",
                expires = io.ktor.util.date.GMTDate.START, maxAge = 0))

            // Read and clear the Razorpay subscription cookies set during anonymous payment-first flow.
            val rpSubscription = call.request.cookies["rp_subscription"]
            val rpPlan = call.request.cookies["rp_plan"]
            call.response.cookies.append(Cookie("rp_subscription", "", path = "/transloom/auth",
                expires = io.ktor.util.date.GMTDate.START, maxAge = 0))
            call.response.cookies.append(Cookie("rp_plan", "", path = "/transloom/auth",
                expires = io.ktor.util.date.GMTDate.START, maxAge = 0))

            val code = call.request.queryParameters["code"]
            if (code == null) {
                call.respondText("Authorization failed: No code provided", status = HttpStatusCode.BadRequest)
                return@get
            }

            if (clientId == "dummy_client_id") {
                log.info("Mocking GitHub OAuth (no GITHUB_CLIENT_ID set)")
                val mockUser = userRepository.upsert(12345L, "mock-developer", "mock@transloom.dev", null, null)
                val mockToken = JWT.create()
                    .withAudience("transloom-app").withIssuer("transloom-backend")
                    .withClaim("userId", mockUser.id).withClaim("githubId", 12345L)
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
                log.warn("GitHub token exchange failed: {}", tokenResponse.error_description)
                call.respondText(
                    "Failed to retrieve access token: ${tokenResponse.error_description}",
                    status = HttpStatusCode.Unauthorized
                )
                return@get
            }

            val githubUser: GitHubUser = httpClient.get("https://api.github.com/user") {
                header(HttpHeaders.Authorization, "Bearer ${tokenResponse.access_token}")
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            }.body()

            val user = userRepository.upsert(
                githubId = githubUser.id, username = githubUser.login,
                email = githubUser.email, avatarUrl = githubUser.avatar_url,
                githubToken = tokenResponse.access_token
            )
            log.info("OAuth success: user={} id={}", githubUser.login, user.id)

            // Payment-first flow: link the pre-paid Razorpay subscription to this user.
            // If linking fails we must NOT silently issue a JWT — the user would land on FREE plan
            // while still being charged. Block the flow and show a billing-error page; the customer's
            // Razorpay subscription remains intact and can be recovered manually from logs.
            if (!rpSubscription.isNullOrBlank() && !rpPlan.isNullOrBlank()) {
                val linked = razorpayService.linkAnonymousSubscription(user.id, rpSubscription, rpPlan)
                if (!linked) {
                    log.error("Failed to link rp_subscription={} to user={} ({}). Aborting OAuth completion.",
                        rpSubscription, user.id, githubUser.login)
                    call.respondRedirect("/transloom?billing_error=link_failed&sub=$rpSubscription")
                    return@get
                }
            }

            val jwtToken = JWT.create()
                .withAudience("transloom-app").withIssuer("transloom-backend")
                .withClaim("userId", user.id).withClaim("githubId", githubUser.id)
                .withClaim("username", githubUser.login)
                .withExpiresAt(Date(System.currentTimeMillis() + 604800000))
                .sign(Algorithm.HMAC256(jwtSecret))

            call.respondRedirect(frontendRedirectUrl + jwtToken)
        }
    }
}
