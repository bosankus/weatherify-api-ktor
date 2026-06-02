package bose.ankush.base

import bose.ankush.data.model.ApiResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.androidplay.core.secrets.getSecretValue
import config.Environment
import config.JwtConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import com.syncling.routes.apiToken
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import util.Constants

fun Application.configureAuthentication() {
    val logger = LoggerFactory.getLogger("Authentication")
    val synclingJwtSecret = getSecretValue("jwt-secret")

    install(Authentication) {
        jwt("jwt-auth") {
            realm = Environment.getJwtRealm()
            verifier(JwtConfig.verifier)

            authHeader { call ->
                val header = call.request.parseAuthorizationHeader()
                if (header != null) return@authHeader header
                val tokenFromCookie: String? = call.request.cookies["jwt_token"]
                if (tokenFromCookie.isNullOrBlank()) return@authHeader null
                HttpAuthHeader.Single("Bearer", tokenFromCookie)
            }

            validate { credential ->
                try {
                    val email = credential.payload.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
                    if (email.isNotEmpty()) {
                        logger.debug("Authentication successful for user: $email")
                        JWTPrincipal(credential.payload)
                    } else {
                        logger.warn("Authentication failed: Empty email claim in token")
                        null
                    }
                } catch (e: Exception) {
                    logger.error("Authentication failed: ${e.message}")
                    null
                }
            }

            challenge { defaultScheme, realm ->
                logger.warn("Authentication challenge triggered - JWT authentication failed")
                call.response.status(HttpStatusCode.Unauthorized)
                call.response.header(HttpHeaders.WWWAuthenticate, "$defaultScheme realm=\"$realm\"")

                val apiResponse = ApiResponse(
                    status = false,
                    message = "Authentication failed: Invalid or expired token. If your token is expired, you can get a new one at the /refresh-token endpoint.",
                    data = mapOf(
                        "refreshEndpoint" to Constants.Api.REFRESH_TOKEN_ENDPOINT,
                        "method" to "POST",
                        "requestFormat" to "{ \"token\": \"your-expired-token\" }"
                    )
                )
                call.respondText(Json.encodeToString(apiResponse), ContentType.Application.Json)
            }
        }

        jwt("auth-jwt") {
            realm = "Syncling API"
            verifier(
                JWT.require(Algorithm.HMAC256(synclingJwtSecret))
                    .withAudience("syncling-app")
                    .withIssuer("syncling-backend")
                    .build()
            )
            validate { credential ->
                val hasUserId = credential.payload.getClaim("userId")?.asString() != null
                val hasGithubId = credential.payload.getClaim("githubId")?.asLong() != null
                if (hasUserId && hasGithubId) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }

        // Accepts `Authorization: Bearer sli_<token>` for CLI / programmatic access.
        // The ApiTokenRepository is resolved lazily from application attributes so this provider
        // can be registered before the Syncling DI wiring runs (see SynclingConfig).
        apiToken("api-token")
    }
}