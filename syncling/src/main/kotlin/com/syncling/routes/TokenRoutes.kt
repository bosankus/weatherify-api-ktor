package com.syncling.routes

import com.syncling.model.ApiError
import com.syncling.repository.ApiTokenRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import java.security.SecureRandom

// ── REST API ──────────────────────────────────────────────────────────────────

@Serializable
data class CreateTokenBody(val name: String)

@Serializable
data class CreateTokenResponse(
    val id: String,
    val name: String,
    val token: String,
    val createdAt: Long
)

@Serializable
data class TokenListItem(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastUsedAt: Long?
)

fun Route.configureTokenApiRoutes(tokenRepository: ApiTokenRepository) {
    route("/syncling/api/me/tokens") {
        get {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val items = tokenRepository.listForUser(userId).map {
                TokenListItem(it.id, it.name, it.createdAt.toEpochMilliseconds(), it.lastUsedAt?.toEpochMilliseconds())
            }
            call.respond(HttpStatusCode.OK, mapOf("tokens" to items))
        }

        post {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val body = runCatching { call.receive<CreateTokenBody>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
            }
            if (body.name.isBlank() || body.name.length > 64)
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("name must be 1–64 characters"))

            if (tokenRepository.listForUser(userId).size >= 10)
                return@post call.respond(HttpStatusCode.Conflict, ApiError("Maximum 10 API tokens per account. Revoke one first."))

            val rawToken = generateApiToken()
            val created = tokenRepository.create(userId, body.name.trim(), sha256(rawToken))
            call.respond(HttpStatusCode.Created, CreateTokenResponse(
                id = created.id,
                name = created.name,
                token = rawToken,
                createdAt = created.createdAt.toEpochMilliseconds()
            ))
        }

        delete("/{id}") {
            val userId = call.userId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val tokenId = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Missing token id"))
            if (tokenRepository.delete(tokenId, userId))
                call.respond(HttpStatusCode.OK, mapOf("revoked" to true))
            else
                call.respond(HttpStatusCode.NotFound, ApiError("Token not found"))
        }
    }
}

// ── Portal page ───────────────────────────────────────────────────────────────

fun Route.configureTokenPortalRoute() {
    get("/syncling/tokens") {
        call.respondHtml { tokensApp() }
    }
}

internal fun HTML.tokensApp() {
    portalShell(
        pageTitle = "API Tokens",
        navKey = "tokens",
        staticStylesheets = listOf("/transloom/static/tokens.css"),
        staticScripts = listOf("/transloom/static/tokens.js"),
        mainClass = "tk-page",
    ) {
        header("tk-header") {
            div("tk-header-text") {
                h1("page-title") { +"API Tokens" }
                p("page-sub") { +"Use tokens to authenticate the Syncling CLI and programmatic API access." }
            }
            button(classes = "bl-btn primary") {
                id = "tk-new-btn"
                type = ButtonType.button
                +"+ New token"
            }
        }

        div {
            id = "tk-list"
            classes = setOf("tk-list")
            // Skeleton rows — replaced by JS on load
            repeat(2) { div("tk-row tk-row-skeleton") }
        }

        div { id = "tk-modal-mount" }

        div("tk-cli-guide") {
            h2 { +"Quickstart" }
            pre("tk-code") {
                +"""# Install
npm install -g @syncling/cli

# Authenticate
syncling login

# Pull translated files
syncling pull <project-id> --lang es --out ./translations"""
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private val rng = SecureRandom()

private fun generateApiToken(): String {
    val bytes = ByteArray(32)
    rng.nextBytes(bytes)
    return "sli_" + bytes.joinToString("") { "%02x".format(it) }
}
