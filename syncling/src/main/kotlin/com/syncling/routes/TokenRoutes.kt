package com.syncling.routes

import com.syncling.domain.BillingPlan
import com.syncling.model.ApiError
import com.syncling.repository.ApiTokenRepository
import com.syncling.repository.BillingRepository
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

fun Route.configureTokenApiRoutes(
    tokenRepository: ApiTokenRepository,
    billingRepository: BillingRepository,
) {
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
            // API tokens & SDK keys are a paid feature. Listing/revoking stays open so a
            // downgraded user can still manage tokens they already minted.
            if (billingRepository.getSubscription(userId).plan == BillingPlan.FREE)
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("API tokens & SDK keys require a paid plan. Upgrade to create one.")
                )
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

fun Route.configureTokenPortalRoute(jwtSecret: String, billingRepository: BillingRepository) {
    get("/tokens") {
        val userId = call.sessionUserId(jwtSecret) ?: run {
            call.respondRedirect("/auth/github")
            return@get
        }
        val isPaid = billingRepository.getSubscription(userId).plan != BillingPlan.FREE
        call.respondHtml { tokensApp(isPaid) }
    }
}

internal fun HTML.tokensApp(isPaid: Boolean) {
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
                p("page-sub") { +"Authenticate the Syncling CLI. Native SDKs are coming soon." }
            }
            if (isPaid) {
                button(classes = "bl-btn primary") {
                    id = "tk-new-btn"
                    type = ButtonType.button
                    +"+ New key"
                }
            }
        }

        // ── Paid-feature gate ────────────────────────────────────────────────
        // Creating tokens is a paid feature. Free users see this gate (mirrors the
        // members page plan gate); any tokens they minted on a previous paid plan
        // still render below so they can revoke them.
        if (!isPaid) {
            div("tk-plan-gate") {
                h3 { +"API tokens & SDK keys are a paid feature" }
                p {
                    +"You're on the "
                    b { +"Free" }
                    +" plan. Upgrade to a paid plan to create CLI tokens and SDK keys for the Syncling CLI, Android, and iOS SDKs."
                }
                a("/billing") { classes = setOf("bl-btn", "primary"); +"Upgrade plan" }
            }
        }

        // ── Token list card ──────────────────────────────────────────────
        div("tk-list-card") {
            id = "tk-list-card"
            attributes["data-paid"] = isPaid.toString()
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

        // ── SDK Quickstart cards ─────────────────────────────────────────────
        div("tk-qs-card tk-qs-soon") {
            div("tk-qs-head") {
                div("tk-qs-platform-icon tk-qs-icon-android") {
                    unsafe {
                        +"""<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-label="Android">
  <path d="M17.523 15.341a.999.999 0 1 1 0-1.998.999.999 0 0 1 0 1.998zm-11.046 0a.999.999 0 1 1 0-1.998.999.999 0 0 1 0 1.998zm11.405-6.02 1.997-3.459a.416.416 0 0 0-.72-.416l-2.022 3.503A11.963 11.963 0 0 0 12 7.851c-1.854 0-3.59.393-5.137 1.098L4.841 5.446a.416.416 0 0 0-.72.416l1.997 3.459C2.689 10.163.343 13.74 0 18h24c-.343-4.26-2.689-7.837-6.118-9.679z"/>
</svg>"""
                    }
                }
                div("tk-qs-head-info") {
                    h2 { +"Android SDK" }
                    span("tk-kmp-badge") { +"Kotlin Multiplatform" }
                }
                span("tk-soon-badge") { +"Yet to come" }
            }
            p("tk-qs-soon-desc") {
                +"The Syncling Android SDK is being built with Kotlin Multiplatform (KMP) and will bring live CDN-backed translations natively to Android apps."
            }
        }

        div("tk-qs-card tk-qs-soon") {
            div("tk-qs-head") {
                div("tk-qs-platform-icon tk-qs-icon-ios") {
                    unsafe {
                        +"""<svg xmlns="http://www.w3.org/2000/svg" width="20" height="22" viewBox="0 0 24 24" fill="currentColor" aria-label="iOS / Apple">
  <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
</svg>"""
                    }
                }
                div("tk-qs-head-info") {
                    h2 { +"iOS SDK" }
                    span("tk-kmp-badge") { +"Kotlin Multiplatform" }
                }
                span("tk-soon-badge") { +"Yet to come" }
            }
            p("tk-qs-soon-desc") {
                +"The Syncling iOS SDK is being built with Kotlin Multiplatform (KMP) and will enable live CDN-backed translations natively on iOS."
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
