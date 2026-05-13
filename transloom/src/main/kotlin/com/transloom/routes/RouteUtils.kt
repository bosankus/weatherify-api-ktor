package com.transloom.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.UUID

internal fun ApplicationCall.userId(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
