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
data class CreateTokenBody(
    val name: String,
    val platforms: List<String>? = null,
    // Legacy single-platform field; kept for backward compatibility with older clients.
    val type: String? = null,
)

@Serializable
data class CreateTokenResponse(
    val id: String,
    val name: String,
    val token: String,
    val createdAt: Long,
    val platforms: List<String>,
)

@Serializable
data class TokenListItem(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastUsedAt: Long?,
    val platforms: List<String>,
)

private val ALLOWED_PLATFORMS = setOf("CLI", "ANDROID", "IOS")

fun Route.configureTokenApiRoutes(tokenRepository: ApiTokenRepository) {
    route("/api/me/tokens") {
        get {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val items = tokenRepository.listForUser(userId).map {
                TokenListItem(it.id, it.name, it.createdAt.toEpochMilliseconds(), it.lastUsedAt?.toEpochMilliseconds(), it.platforms)
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

            val requested = (body.platforms ?: listOfNotNull(body.type))
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .ifEmpty { listOf("CLI") }
            if (requested.any { it !in ALLOWED_PLATFORMS })
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("platforms must be any of CLI, ANDROID, IOS"))

            if (tokenRepository.listForUser(userId).size >= 10)
                return@post call.respond(HttpStatusCode.Conflict, ApiError("Maximum 10 API tokens per account. Revoke one first."))

            val rawToken = generateApiToken(requested)
            val created = tokenRepository.create(userId, body.name.trim(), sha256(rawToken), requested)
            call.respond(HttpStatusCode.Created, CreateTokenResponse(
                id = created.id,
                name = created.name,
                token = rawToken,
                createdAt = created.createdAt.toEpochMilliseconds(),
                platforms = created.platforms,
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
    get("/tokens") {
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
        // ── Header ──────────────────────────────────────────────────────────
        div("tk-header") {
            div("tk-header-text") {
                h1("page-title") { +"API Tokens & SDK Keys" }
                p("page-sub") { +"Authenticate the Syncling CLI, Android SDK, and iOS SDK." }
            }
            button(classes = "bl-btn primary") {
                id = "tk-new-btn"
                type = ButtonType.button
                +"+ New key"
            }
        }

        // ── Token list card ──────────────────────────────────────────────
        div("tk-list-card") {
            id = "tk-list-card"
            div("tk-list-head") {
                h2 { +"Active tokens & keys" }
                span("tk-list-head-meta") { id = "tk-list-meta" }
            }
            div {
                id = "tk-list-body"
                // Skeleton rows replaced by JS on load
                repeat(3) { div("tk-row tk-row-skeleton") }
            }
        }

        div { id = "tk-modal-mount" }

        // ── SDK Quickstart card ──────────────────────────────────────────────
        div("tk-qs-card") {
            div("tk-qs-head") {
                h2 { +"Android SDK" }
            }
            pre("tk-code") {
                unsafe {
                    +"""<span class="tk-c-comment">// 1. Add dependency (build.gradle.kts)</span>
<span class="tk-c-cmd">implementation("space.syncling:syncling-android:1.0.0")</span>

<span class="tk-c-comment">// 2. Initialize in Application.onCreate()</span>
<span class="tk-c-cmd">Syncling.init(context, apiKey = "slk_your_android_key", projectId = "&lt;project-id&gt;")</span>

<span class="tk-c-comment">// 3. Use translations (returns live CDN string)</span>
<span class="tk-c-cmd">val greeting = Syncling.getString("welcome_message")</span>"""
                }
            }
        }

        div("tk-qs-card") {
            div("tk-qs-head") {
                h2 { +"iOS SDK" }
            }
            pre("tk-code") {
                unsafe {
                    +"""<span class="tk-c-comment">// 1. Add via Swift Package Manager</span>
<span class="tk-c-cmd">// URL: https://github.com/syncling/syncling-ios</span>

<span class="tk-c-comment">// 2. Initialize in AppDelegate / @main App</span>
<span class="tk-c-cmd">Syncling.configure(apiKey: "slk_your_ios_key", projectId: "&lt;project-id&gt;")</span>

<span class="tk-c-comment">// 3. Use translations</span>
<span class="tk-c-cmd">let greeting = Syncling.string("welcome_message")</span>"""
                }
            }
        }

        div("tk-qs-card") {
            div("tk-qs-head") {
                h2 { +"CLI" }
            }
            pre("tk-code") {
                unsafe {
                    +"""<span class="tk-c-comment"># Install the CLI</span>
<span class="tk-c-cmd">npm install -g syncling</span>

<span class="tk-c-comment"># Authenticate with your CLI token</span>
<span class="tk-c-cmd">syncling login</span>

<span class="tk-c-comment"># Pull translated files for a project</span>
<span class="tk-c-cmd">syncling pull &lt;project-id&gt; --lang es --out ./translations</span>"""
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private val rng = SecureRandom()

private fun generateApiToken(platforms: List<String>): String {
    val bytes = ByteArray(32)
    rng.nextBytes(bytes)
    // sli_ for CLI-only tokens; slk_ as soon as any SDK platform is included.
    val prefix = if (platforms.any { it == "ANDROID" || it == "IOS" }) "slk_" else "sli_"
    return prefix + bytes.joinToString("") { "%02x".format(it) }
}
