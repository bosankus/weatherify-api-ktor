package bose.ankush.route

import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import com.androidplay.core.common.Result
import com.androidplay.core.secrets.getSecretValue
import config.AtlassianOAuthConfig
import data.atlassian.AtlassianApiClient
import data.atlassian.AtlassianLogRedactor
import data.atlassian.AtlassianOAuthClient
import data.atlassian.AtlassianOAuthStateStore
import data.atlassian.AtlassianTokenStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.AuthHelper.getAuthenticatedAdminOrRespond
import util.AuthHelper.requireBotSharedSecretOrRespond

private val botAtlassianLog = LoggerFactory.getLogger("BotAtlassianRoute")

/**
 * Bot-facing Atlassian proxy routes. Bots call /bot/atlassian/... only —
 * Atlassian access tokens never leave middleware.
 *
 * /bot/atlassian/... requires the bot shared secret
 * (bot-atlassian-shared-secret / BOT_ATLASSIAN_SHARED_SECRET) via
 * Authorization: Bearer or X-Bot-Token.
 *
 * Admin bootstrap OAuth start/callback live under /admin/atlassian/oauth/... . The
 * start endpoint requires an admin JWT (getAuthenticatedAdminOrRespond); the callback
 * relies on single-use OAuth state because the provider redirect cannot carry a
 * SameSite=Strict jwt_token cookie. Both enforce the fixed redirect-URI allowlist/state
 * flow.
 *
 * APE-10
 */
fun Route.botAtlassianRoute() {
    val config: AtlassianOAuthConfig by inject()
    val tokenStore: AtlassianTokenStore by inject()
    val oauthClient: AtlassianOAuthClient by inject()
    val apiClient: AtlassianApiClient by inject()
    val stateStore: AtlassianOAuthStateStore by inject()
    val botSharedSecret = getSecretValue(AtlassianOAuthConfig.SECRET_BOT_SHARED)

    route("/bot/atlassian") {
        get("/health") {
            if (!call.requireBotSharedSecretOrRespond(botSharedSecret)) return@get
            val health = tokenStore.health()
            val payload = AtlassianHealthResponse(
                configured = config.isConfigured(),
                baseUrl = config.baseUrl,
                cloudId = config.cloudId,
                defaultProject = config.defaultProject,
                scopes = config.scopes,
                hasRefreshToken = health.hasRefreshToken,
                hasAccessToken = health.hasAccessToken,
                accessTokenFresh = health.accessTokenFresh,
                // Epoch only — never the token itself.
                accessExpiresAtEpochMs = health.accessExpiresAtEpochMs,
                refreshTokenPersistOk = health.refreshTokenPersistOk,
            )
            call.respondSuccess("Atlassian bot auth health", payload)
        }

        get("/myself") {
            if (!call.requireBotSharedSecretOrRespond(botSharedSecret)) return@get
            when (val result = apiClient.getMyself()) {
                is Result.Success -> call.respondSuccess("Atlassian myself", result.data)
                is Result.Error -> {
                    botAtlassianLog.warn(AtlassianLogRedactor.redact(result.message))
                    call.respondError(
                        AtlassianLogRedactor.redact(result.message),
                        Unit,
                        HttpStatusCode.BadGateway,
                    )
                }
            }
        }

        get("/project") {
            if (!call.requireBotSharedSecretOrRespond(botSharedSecret)) return@get
            when (val result = apiClient.getProject(config.defaultProject)) {
                is Result.Success -> call.respondSuccess("Atlassian project", result.data)
                is Result.Error -> {
                    botAtlassianLog.warn(AtlassianLogRedactor.redact(result.message))
                    call.respondError(
                        AtlassianLogRedactor.redact(result.message),
                        Unit,
                        HttpStatusCode.BadGateway,
                    )
                }
            }
        }

        get("/project/{key}") {
            if (!call.requireBotSharedSecretOrRespond(botSharedSecret)) return@get
            val key = call.parameters["key"]?.trim().orEmpty()
            if (key.isEmpty()) {
                call.respondError("Missing project key", Unit, HttpStatusCode.BadRequest)
                return@get
            }
            if (!config.isProjectKeyAllowed(key)) {
                call.respondError(
                    "Project key is not allowed (bots are constrained to ${config.defaultProject})",
                    Unit,
                    HttpStatusCode.Forbidden,
                )
                return@get
            }
            when (val result = apiClient.getProject(key)) {
                is Result.Success -> call.respondSuccess("Atlassian project", result.data)
                is Result.Error -> {
                    botAtlassianLog.warn(AtlassianLogRedactor.redact(result.message))
                    call.respondError(
                        AtlassianLogRedactor.redact(result.message),
                        Unit,
                        HttpStatusCode.BadGateway,
                    )
                }
            }
        }
    }

    route("/admin/atlassian/oauth") {
        get("/start") {
            call.getAuthenticatedAdminOrRespond() ?: return@get
            val redirectUri = call.request.queryParameters["redirect_uri"]
                ?: config.redirectUriAllowlist.firstOrNull()
            if (redirectUri.isNullOrBlank()) {
                call.respondError("No redirect URI configured", Unit, HttpStatusCode.BadRequest)
                return@get
            }
            if (!config.isRedirectUriAllowed(redirectUri)) {
                call.respondError(
                    "Redirect URI is not on the fixed allowlist",
                    Unit,
                    HttpStatusCode.Forbidden,
                )
                return@get
            }
            val state = stateStore.issue()
            when (val url = oauthClient.buildAuthorizationUrl(redirectUri, state)) {
                is Result.Success -> {
                    botAtlassianLog.info("Starting Atlassian OAuth bootstrap (state issued, redirect allowlisted)")
                    call.respondRedirect(url.data)
                }
                is Result.Error -> call.respondError(
                    AtlassianLogRedactor.redact(url.message),
                    Unit,
                    HttpStatusCode.BadRequest,
                )
            }
        }

        get("/callback") {
            // The OAuth provider's cross-site redirect does not carry our SameSite=Strict
            // jwt_token cookie. /start authenticates the admin; this single-use state
            // validates the callback and provides CSRF protection.
            val code = call.request.queryParameters["code"]
            val state = call.request.queryParameters["state"]
            val error = call.request.queryParameters["error"]
            val redirectUri = call.request.queryParameters["redirect_uri"]
                ?: config.redirectUriAllowlist.firstOrNull()

            if (!stateStore.consume(state)) {
                botAtlassianLog.warn("Atlassian OAuth callback rejected: missing, mismatched, expired, or replayed state")
                call.respondText(
                    "Invalid or expired OAuth state",
                    status = HttpStatusCode.Forbidden,
                )
                return@get
            }

            if (!error.isNullOrBlank()) {
                val safe = AtlassianLogRedactor.redact(error)
                botAtlassianLog.warn("Atlassian OAuth callback error: {}", safe)
                call.respondText(
                    "Atlassian OAuth failed: $safe",
                    status = HttpStatusCode.BadRequest,
                )
                return@get
            }
            if (code.isNullOrBlank()) {
                call.respondText("Missing authorization code", status = HttpStatusCode.BadRequest)
                return@get
            }
            if (redirectUri.isNullOrBlank() || !config.isRedirectUriAllowed(redirectUri)) {
                call.respondText(
                    "Redirect URI is not on the fixed allowlist",
                    status = HttpStatusCode.Forbidden,
                )
                return@get
            }

            when (val exchanged = oauthClient.exchangeAuthorizationCode(code, redirectUri)) {
                is Result.Success -> {
                    botAtlassianLog.info(
                        "Atlassian OAuth bootstrap complete; refresh token stored in memory " +
                            "(durable SM persist attempted on rotation)",
                    )
                    call.respondText(
                        "Atlassian OAuth bootstrap succeeded. Refresh token is in memory; " +
                            "rotation also attempts Secret Manager key atlassian-oauth-refresh-token.",
                        status = HttpStatusCode.OK,
                    )
                }
                is Result.Error -> {
                    botAtlassianLog.warn(AtlassianLogRedactor.redact(exchanged.message))
                    call.respondText(
                        AtlassianLogRedactor.redact(exchanged.message),
                        status = HttpStatusCode.BadGateway,
                    )
                }
            }
        }
    }
}

@Serializable
data class AtlassianHealthResponse(
    val configured: Boolean,
    val baseUrl: String,
    val cloudId: String,
    val defaultProject: String,
    val scopes: List<String>,
    val hasRefreshToken: Boolean,
    val hasAccessToken: Boolean,
    val accessTokenFresh: Boolean,
    val accessExpiresAtEpochMs: Long? = null,
    val refreshTokenPersistOk: Boolean = true,
)
