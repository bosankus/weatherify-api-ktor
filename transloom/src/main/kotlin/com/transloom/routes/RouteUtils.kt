package com.transloom.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.Date
import java.util.UUID

internal fun ApplicationCall.userId(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }

internal const val JWT_TTL_MS = 7L * 24 * 60 * 60 * 1000

internal fun mintJwt(secret: String, userId: String, githubId: Long, username: String): String =
    JWT.create()
        .withAudience("transloom-app").withIssuer("transloom-backend")
        .withClaim("userId", userId).withClaim("githubId", githubId)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + JWT_TTL_MS))
        .sign(Algorithm.HMAC256(secret))
