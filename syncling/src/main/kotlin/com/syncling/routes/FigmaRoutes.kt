package com.syncling.routes

import com.syncling.domain.BillingPlan
import com.syncling.domain.FigmaCandidateStatus
import com.syncling.domain.FigmaStringCandidate
import com.syncling.domain.ProjectRole
import com.syncling.model.ApiError
import com.syncling.model.StructuredApiError
import com.syncling.repository.BillingRepository
import com.syncling.repository.FigmaCandidateRepository
import com.syncling.repository.FigmaPreviewRepository
import com.syncling.repository.FigmaSettingsRepository
import com.syncling.repository.ProjectMembershipRepository
import com.syncling.repository.ProjectRepository
import com.syncling.repository.UserRepository
import com.syncling.services.FigmaFrameUpload
import com.syncling.services.FigmaKeyConflictException
import com.syncling.services.FigmaPushNode
import com.syncling.services.FigmaSyncService
import com.syncling.services.isValidStringKey
import com.syncling.services.requireProjectRole
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

private val figmaLog = LoggerFactory.getLogger("FigmaRoutes")

/** Hard cap on text nodes per plugin push — one screen selection, not a whole design system. */
private const val MAX_NODES_PER_PUSH = 200
private const val MAX_TEXT_LENGTH = 2000

/** Frame screenshots per push: enough for a screen-sized selection, small enough to bound payloads. */
private const val MAX_PREVIEWS_PER_PUSH = 20

/** ~375 KB of PNG per frame once base64-decoded. */
private const val MAX_PREVIEW_BASE64_LENGTH = 500_000

private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

// ── DTOs ──────────────────────────────────────────────────────────────────────

@Serializable
data class FigmaPushNodeBody(
    val nodeId: String,
    val nodeName: String = "",
    val text: String,
    val pageName: String? = null,
    val frameName: String? = null,
    val frameId: String? = null,
    val fixedWidth: Boolean = false,
)

@Serializable
data class FigmaFramePreviewBody(
    val frameId: String,
    /** Base64-encoded PNG, exported scaled-down by the plugin. */
    val png: String,
)

@Serializable
data class FigmaPushBody(
    val projectId: String,
    val fileKey: String,
    val nodes: List<FigmaPushNodeBody>,
    val previews: List<FigmaFramePreviewBody> = emptyList(),
)

@Serializable
data class FigmaPushWarning(val nodeId: String, val text: String, val message: String)

@Serializable
data class FigmaPushResponse(
    val staged: Int,
    val updates: Int,
    val duplicates: Int,
    val skippedUnchanged: Int,
    val skippedRejected: Int,
    val pendingTotal: Int,
    /** Length-overflow heads-ups for the designer — informational only. */
    val warnings: List<FigmaPushWarning> = emptyList(),
    /** Set when auto-approve turned this push straight into a PR. */
    val autoPrUrl: String? = null,
)

@Serializable
data class FigmaSettingsResponse(val autoApprove: Boolean)

@Serializable
data class FigmaSettingsBody(val autoApprove: Boolean)

@Serializable
data class FigmaDriftItemResponse(
    val stringKey: String,
    val figmaFileKey: String,
    val figmaNodeId: String,
    val figmaText: String,
    val repoText: String,
)

@Serializable
data class FigmaDriftResponse(val items: List<FigmaDriftItemResponse>)

@Serializable
data class FigmaCandidateResponse(
    val id: String,
    val figmaFileKey: String,
    val figmaNodeId: String,
    val nodeName: String,
    val pageName: String?,
    val frameName: String?,
    val figmaFrameId: String?,
    val sourceText: String,
    val suggestedKey: String,
    val finalKey: String?,
    val effectiveKey: String,
    val status: String,
    val duplicateOfKey: String?,
    val similarToKey: String?,
    val similarityScore: Float?,
    val boundKey: String?,
    val prUrl: String?,
    val updatedAt: Long,
)

@Serializable
data class FigmaCandidateListResponse(val candidates: List<FigmaCandidateResponse>, val total: Int)

@Serializable
data class FigmaEditKeyBody(val key: String)

@Serializable
data class FigmaApproveBody(val ids: List<String>, val targetFile: String? = null)

@Serializable
data class FigmaApproveResponse(
    val prUrl: String,
    val branchName: String,
    val keysAdded: List<String>,
    val keysUpdated: List<String>,
)

@Serializable
data class FigmaRejectBody(val ids: List<String>)

@Serializable
data class FigmaConflictResponse(val error: String, val conflicts: Map<String, String>)

private fun FigmaStringCandidate.toResponse() = FigmaCandidateResponse(
    id = id,
    figmaFileKey = figmaFileKey,
    figmaNodeId = figmaNodeId,
    nodeName = nodeName,
    pageName = pageName,
    frameName = frameName,
    figmaFrameId = figmaFrameId,
    sourceText = sourceText,
    suggestedKey = suggestedKey,
    finalKey = finalKey,
    effectiveKey = effectiveKey,
    status = status.name,
    duplicateOfKey = duplicateOfKey,
    similarToKey = similarToKey,
    similarityScore = similarityScore,
    boundKey = boundKey,
    prUrl = prUrl,
    updatedAt = updatedAt.toEpochMilliseconds(),
)

// ── Routes ────────────────────────────────────────────────────────────────────

/**
 * Figma sync endpoints. Registered inside the `authenticate("auth-jwt", "api-token")`
 * block, so the plugin authenticates with a paid-plan API token (`sli_`/`slk_`) and the
 * portal inbox with the session JWT — both resolve through `call.userId()`.
 */
fun Route.configureFigmaRoutes(
    figmaSyncService: FigmaSyncService,
    candidateRepository: FigmaCandidateRepository,
    projectRepository: ProjectRepository,
    userRepository: UserRepository,
    billingRepository: BillingRepository,
    memberships: ProjectMembershipRepository,
    previewRepository: FigmaPreviewRepository? = null,
    settingsRepository: FigmaSettingsRepository? = null,
) {
    route("/api/figma") {

        // Plugin push: stage extracted text nodes into the project's Figma inbox.
        post("/push") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val body = runCatching { call.receive<FigmaPushBody>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
            }
            val projectId = runCatching { UUID.fromString(body.projectId).toString() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            }
            if (body.fileKey.isBlank() || body.fileKey.length > 128) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("fileKey is required"))
            }
            if (body.nodes.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("nodes must not be empty"))
            }
            if (body.nodes.size > MAX_NODES_PER_PUSH) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Maximum $MAX_NODES_PER_PUSH nodes per push"))
            }
            if (body.nodes.any { it.text.length > MAX_TEXT_LENGTH }) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Text must be $MAX_TEXT_LENGTH characters or less"))
            }
            if (body.previews.size > MAX_PREVIEWS_PER_PUSH) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Maximum $MAX_PREVIEWS_PER_PUSH frame previews per push"))
            }
            // Oversized, malformed, or non-PNG previews are dropped, not rejected — screenshots
            // are context, never worth failing the string push over.
            val previews = body.previews.mapNotNull { p ->
                if (p.frameId.isBlank() || p.png.length > MAX_PREVIEW_BASE64_LENGTH) return@mapNotNull null
                val bytes = runCatching { java.util.Base64.getDecoder().decode(p.png) }.getOrNull()
                    ?: return@mapNotNull null
                if (bytes.size < PNG_MAGIC.size || !bytes.copyOfRange(0, PNG_MAGIC.size).contentEquals(PNG_MAGIC)) {
                    return@mapNotNull null
                }
                FigmaFrameUpload(frameId = p.frameId, png = bytes)
            }

            val project = projectRepository.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.TRANSLATOR, memberships)
                ?: return@post
            // Figma sync rides the project owner's subscription, like OTA and per-project quotas.
            if (billingRepository.getSubscription(project.ownerId).plan == BillingPlan.FREE) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("Figma sync requires a PRO or Team plan. Upgrade to push strings from Figma."),
                )
            }

            // Auto-approve rides the owner's GitHub token, same as manual approval. A missing
            // token silently downgrades to inbox mode — the push itself must never fail on it.
            val autoApprove = settingsRepository?.get(projectId)?.autoApprove == true
            val autoApproveToken = if (autoApprove) userRepository.findById(project.ownerId)?.githubToken else null

            val summary = figmaSyncService.ingest(
                project = project,
                submittedByUserId = userId,
                fileKey = body.fileKey.trim(),
                nodes = body.nodes.map {
                    FigmaPushNode(
                        nodeId = it.nodeId, nodeName = it.nodeName, text = it.text,
                        pageName = it.pageName, frameName = it.frameName, frameId = it.frameId,
                        fixedWidth = it.fixedWidth,
                    )
                },
                previews = previews,
                autoApprove = autoApprove,
                githubToken = autoApproveToken,
            )
            val pendingTotal = candidateRepository.countForProject(projectId, FigmaCandidateStatus.PENDING)
            call.respond(
                HttpStatusCode.OK,
                FigmaPushResponse(
                    staged = summary.staged,
                    updates = summary.updates,
                    duplicates = summary.duplicates,
                    skippedUnchanged = summary.skippedUnchanged,
                    skippedRejected = summary.skippedRejected,
                    pendingTotal = pendingTotal,
                    warnings = summary.warnings.map { FigmaPushWarning(it.nodeId, it.text, it.message) },
                    autoPrUrl = summary.autoPrUrl,
                ),
            )
        }

        // Drift report: bound keys whose repo copy changed since the last Figma sync.
        get("/projects/{projectId}/drift") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["projectId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

            val project = projectRepository.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                ?: return@get

            val items = figmaSyncService.detectDrift(project)
            call.respond(
                HttpStatusCode.OK,
                FigmaDriftResponse(
                    items.map {
                        FigmaDriftItemResponse(it.stringKey, it.figmaFileKey, it.figmaNodeId, it.figmaText, it.repoText)
                    },
                ),
            )
        }

        // Per-project sync preferences (currently just auto-approve).
        get("/projects/{projectId}/settings") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["projectId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

            val project = projectRepository.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                ?: return@get

            val settings = settingsRepository?.get(projectId)
            call.respond(HttpStatusCode.OK, FigmaSettingsResponse(autoApprove = settings?.autoApprove == true))
        }

        put("/projects/{projectId}/settings") {
            val userId = call.userId()
                ?: return@put call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["projectId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@put call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val body = runCatching { call.receive<FigmaSettingsBody>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, ApiError("autoApprove is required"))
            }
            val repo = settingsRepository
                ?: return@put call.respond(HttpStatusCode.NotFound, ApiError("Settings are not available"))

            val project = projectRepository.findById(projectId)
            // Auto-approve writes to the repo without human review — restrict the switch to admins.
            call.requireProjectRole(project, userId, ProjectRole.ADMIN, memberships)
                ?: return@put
            if (body.autoApprove && billingRepository.getSubscription(project.ownerId).plan == BillingPlan.FREE) {
                return@put call.respond(HttpStatusCode.Forbidden, ApiError("Figma sync requires a PRO or Team plan."))
            }

            val updated = repo.setAutoApprove(projectId, body.autoApprove)
            call.respond(HttpStatusCode.OK, FigmaSettingsResponse(autoApprove = updated.autoApprove))
        }

        // Inbox listing for the portal (and plugin status badges).
        get("/projects/{projectId}/candidates") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["projectId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

            val project = projectRepository.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                ?: return@get

            val status = call.request.queryParameters["status"]?.takeIf { it.isNotBlank() }
                ?.let { s -> FigmaCandidateStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } }
                ?: FigmaCandidateStatus.PENDING
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 100
            val offset = call.request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

            val items = candidateRepository.listForProject(projectId, status, limit, offset)
            val total = candidateRepository.countForProject(projectId, status)
            call.respond(HttpStatusCode.OK, FigmaCandidateListResponse(items.map { it.toResponse() }, total))
        }

        // Frame screenshot for inbox context. Shared by every candidate from the same frame,
        // so the URL is frame-keyed and browser-cacheable across candidates.
        get("/projects/{projectId}/preview") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["projectId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val fileKey = call.request.queryParameters["fileKey"]?.takeIf { it.isNotBlank() && it.length <= 128 }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("fileKey is required"))
            val frameId = call.request.queryParameters["frameId"]?.takeIf { it.isNotBlank() && it.length <= 128 }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("frameId is required"))

            val project = projectRepository.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                ?: return@get

            val preview = previewRepository?.find(projectId, fileKey, frameId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("No preview for this frame"))
            call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
            call.respondBytes(preview.png, ContentType.Image.PNG)
        }

        // Dev edits the key before approving.
        patch("/candidates/{id}") {
            val userId = call.userId()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val candidateId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid candidate id"))
            val body = runCatching { call.receive<FigmaEditKeyBody>() }.getOrElse {
                return@patch call.respond(HttpStatusCode.BadRequest, ApiError("key is required"))
            }
            val key = body.key.trim()
            if (!isValidStringKey(key)) {
                return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("Invalid key — use lowercase letters, digits, and underscores (max 80 chars)"),
                )
            }

            val candidate = candidateRepository.findById(candidateId)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ApiError("Candidate not found"))
            val project = projectRepository.findById(candidate.projectId)
            call.requireProjectRole(
                project, userId, ProjectRole.TRANSLATOR, memberships,
                denyStatus = HttpStatusCode.Forbidden, denyMessage = "Access denied",
            ) ?: return@patch
            if (candidate.boundKey != null) {
                return@patch call.respond(
                    HttpStatusCode.Conflict,
                    ApiError("This node is bound to '${candidate.boundKey}' — approving updates that key in place"),
                )
            }

            if (candidateRepository.updateFinalKey(candidateId, candidate.projectId, key)) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "updated", "id" to candidateId, "key" to key))
            } else {
                call.respond(HttpStatusCode.Conflict, ApiError("Candidate is no longer pending"))
            }
        }

        // Approve candidates → merge into the source file and open a PR against the watch branch.
        post("/projects/{projectId}/approve") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["projectId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val body = runCatching { call.receive<FigmaApproveBody>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("ids must not be empty"))
            }
            if (body.ids.isEmpty() || body.ids.size > MAX_NODES_PER_PUSH) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("ids must contain 1–$MAX_NODES_PER_PUSH entries"))
            }

            val project = projectRepository.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.TRANSLATOR, memberships)
                ?: return@post
            if (billingRepository.getSubscription(project.ownerId).plan == BillingPlan.FREE) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("Figma sync requires a PRO or Team plan."),
                )
            }
            val targetFile = body.targetFile ?: project.sourceFilePaths.firstOrNull()
            if (targetFile == null || targetFile !in project.sourceFilePaths) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("targetFile must be one of the project's source files"),
                )
            }
            // PRs are opened with the project owner's GitHub token, same as the pipeline.
            val githubToken = userRepository.findById(project.ownerId)?.githubToken
                ?: return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    StructuredApiError(
                        error = "Project owner's GitHub access has expired.",
                        code = "GITHUB_REAUTH_REQUIRED",
                        actionHint = "The project owner must reconnect GitHub.",
                        reauthUrl = "/auth/github",
                    ),
                )

            val result = try {
                figmaSyncService.approve(project, body.ids, targetFile, githubToken)
            } catch (e: FigmaKeyConflictException) {
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    FigmaConflictResponse("Resolve key conflicts before approving", e.conflicts),
                )
            } catch (e: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError(e.message ?: "Nothing to approve"))
            } catch (e: Exception) {
                figmaLog.error("Figma approve failed: project={} error={}", projectId, e.message)
                return@post call.respond(HttpStatusCode.BadGateway, ApiError("Could not open the PR on GitHub. Please try again."))
            }
            call.respond(
                HttpStatusCode.OK,
                FigmaApproveResponse(
                    prUrl = result.prUrl,
                    branchName = result.branchName,
                    keysAdded = result.keysAdded,
                    keysUpdated = result.keysUpdated,
                ),
            )
        }

        post("/projects/{projectId}/reject") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["projectId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val body = runCatching { call.receive<FigmaRejectBody>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("ids must not be empty"))
            }
            if (body.ids.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("ids must not be empty"))
            }

            val project = projectRepository.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.TRANSLATOR, memberships)
                ?: return@post

            val rejected = figmaSyncService.reject(projectId, body.ids)
            call.respond(HttpStatusCode.OK, mapOf("rejected" to rejected))
        }
    }
}
