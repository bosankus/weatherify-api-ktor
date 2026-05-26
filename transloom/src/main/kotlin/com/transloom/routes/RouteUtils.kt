package com.transloom.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.transloom.domain.BillingPlan
import com.transloom.model.ApiError
import com.transloom.repository.BillingRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import java.util.Date
import java.util.UUID

internal fun ApplicationCall.userId(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }

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
        .withAudience("transloom-app").withIssuer("transloom-backend")
        .withClaim("userId", userId).withClaim("githubId", githubId)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + JWT_TTL_MS))
        .sign(Algorithm.HMAC256(secret))
