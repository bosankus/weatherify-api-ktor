package com.syncling.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.syncling.domain.BillingPlan
import com.syncling.model.ApiError
import com.syncling.repository.ApiTokenRepository
import com.syncling.repository.BillingRepository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Date
import java.util.UUID

/** Principal produced by the `api-token` auth provider for CLI/programmatic access. */
class ApiTokenPrincipal(val userId: String, val tokenId: String)

/** Application-level attribute key so [ApiTokenAuthProvider] can resolve the repository lazily. */
val ApiTokenRepoAttr = AttributeKey<ApiTokenRepository>("ApiTokenRepository")

/** Registers the [ApiTokenAuthProvider] under [name] in the Authentication plugin config. */
fun AuthenticationConfig.apiToken(name: String = "api-token") {
    register(ApiTokenAuthProvider(ApiTokenAuthProvider.Config(name)))
}

/**
 * Custom Ktor auth provider that accepts `Authorization: Bearer sli_<token>` headers.
 * The repository is resolved lazily from application attributes so this provider can be
 * registered before the Syncling DI wiring runs.
 */
class ApiTokenAuthProvider(config: Config) : AuthenticationProvider(config) {
    class Config(name: String) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val authHeader = call.request.headers[HttpHeaders.Authorization] ?: return
        if (!authHeader.startsWith("Bearer sli_") && !authHeader.startsWith("Bearer slk_")) return

        val rawToken = authHeader.removePrefix("Bearer ")
        val hash = sha256(rawToken)
        val repo = runCatching { call.application.attributes[ApiTokenRepoAttr] }.getOrNull() ?: run {
            context.error("api-token", AuthenticationFailedCause.NoCredentials)
            context.challenge("api-token", AuthenticationFailedCause.NoCredentials) { challenge, c ->
                c.respond(HttpStatusCode.Unauthorized, ApiError("Authentication failed: API token repository not found"))
                challenge.complete()
            }
            return
        }

        val token = repo.findByHash(hash) ?: run {
            context.error("api-token", AuthenticationFailedCause.InvalidCredentials)
            context.challenge("api-token", AuthenticationFailedCause.InvalidCredentials) { challenge, c ->
                c.respond(HttpStatusCode.Unauthorized, ApiError("Invalid API token"))
                challenge.complete()
            }
            return
        }

        call.application.launch { repo.touch(token.id) }
        context.principal(ApiTokenPrincipal(token.userId, token.id))
    }
}

internal fun ApplicationCall.userId(): String? {
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
        ?.let { return it }
    return principal<ApiTokenPrincipal>()?.userId
}

/**
 * Returns the user's current plan if it satisfies [minPlan] (ordinal compare), otherwise
 * responds 403 and returns null so the caller can `?: return@get`. Used by analytics
 * endpoints that are part of paid tiers.
 */
internal suspend fun ApplicationCall.requirePlan(
    minPlan: BillingPlan,
    billing: BillingRepository,
    userId: String
): BillingPlan? {
    val plan = billing.getSubscription(userId).plan
    if (plan.ordinal < minPlan.ordinal) {
        respond(HttpStatusCode.Forbidden, ApiError("This feature requires the ${minPlan.displayName} plan or higher."))
        return null
    }
    return plan
}

internal const val JWT_TTL_MS = 7L * 24 * 60 * 60 * 1000

internal fun mintJwt(secret: String, userId: String, githubId: Long, username: String): String =
    JWT.create()
        .withAudience("syncling-app").withIssuer("syncling-backend")
        .withClaim("userId", userId).withClaim("githubId", githubId)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + JWT_TTL_MS))
        .sign(Algorithm.HMAC256(secret))

internal fun sha256(input: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
