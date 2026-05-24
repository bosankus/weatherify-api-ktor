package com.transloom.routes

import com.transloom.model.ApiError
import com.transloom.repository.CdnPublishRepository
import com.transloom.repository.ProjectRepository
import com.transloom.services.CdnPublishException
import com.transloom.services.CdnPublishService
import com.transloom.services.CloudflareKvService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID

private val cdnLog = LoggerFactory.getLogger("CdnRoutes")

@Serializable
data class PublishReceiptResponse(
    val publishedAt: Long,
    val locales: List<String>,
    val bundleVersion: String,
    val skipped: Boolean = false,
    val skipReason: String? = null,
    val promoted: Boolean = false
)

@Serializable
data class BundleMetaResponse(
    val version: String,
    val publishedAt: Long,
    val locales: List<String>,
    val active: Boolean
)

@Serializable
data class VersionEntry(
    val version: String,
    val publishedAt: Long,
    val locales: List<String>,
    val active: Boolean
)

@Serializable
data class VersionsResponse(
    val active: String?,
    val versions: List<VersionEntry>
)

fun Route.configureCdnBundleRoutes(
    projectRepository: ProjectRepository,
    cdnPublishRepository: CdnPublishRepository,
    cdnPublishService: CdnPublishService,
    cfKv: CloudflareKvService
) {
    route("/transloom/api/projects/{id}/bundle") {
        get {
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

            if (projectRepository.findById(projectId) == null) {
                return@get call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
            }

            val activeVersion = cdnPublishRepository.getActiveVersion(projectId)
            val record = activeVersion
                ?.let { cdnPublishRepository.findByVersion(projectId, it) }
                ?: cdnPublishRepository.lastPublish(projectId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("No bundles published for this project"))

            call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=60")
            call.respond(
                HttpStatusCode.OK,
                BundleMetaResponse(
                    version = record.bundleVersion,
                    publishedAt = record.publishedAt,
                    locales = record.locales,
                    active = record.bundleVersion == activeVersion
                )
            )
        }

        get("/{locale}") {
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

            val locale = call.parameters["locale"]?.takeIf { it.isNotBlank() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid locale"))

            // Resolve the active version via the publish service (KV pointer → versioned bundle).
            // If no active pointer exists yet, fall back to the most recent successful publish so
            // pre-Phase-2 projects keep working until they're republished.
            val resolved = cdnPublishService.fetchActiveBundle(projectId, locale)
            val (version, rawBundle, publishedAt) = if (resolved != null) {
                val pubAt = cdnPublishRepository.findByVersion(projectId, resolved.version)?.publishedAt
                    ?: System.currentTimeMillis()
                Triple(resolved.version, resolved.json, pubAt)
            } else {
                val last = cdnPublishRepository.lastPublish(projectId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("No bundles published for this project"))
                val raw = cfKv.get("$projectId:$locale")
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Bundle not found for locale '$locale'"))
                Triple(last.bundleVersion, raw, last.publishedAt)
            }

            val clientEtag = call.request.headers[HttpHeaders.IfNoneMatch]
            if (clientEtag == version) {
                return@get call.respond(HttpStatusCode.NotModified)
            }

            val bundleObj = runCatching { Json.parseToJsonElement(rawBundle) as JsonObject }.getOrElse {
                cdnLog.error("Failed to parse KV bundle for project={} locale={} version={}", projectId, locale, version)
                return@get call.respond(HttpStatusCode.InternalServerError, ApiError("Bundle parse error"))
            }

            if (bundleObj.containsKey("_meta")) {
                cdnLog.warn("Bundle for project={} locale={} contains reserved key '_meta' — it will be overwritten in the response", projectId, locale)
            }

            val responseObj = JsonObject(buildMap {
                put("_meta", buildJsonObject {
                    put("version", JsonPrimitive(version))
                    put("locale", JsonPrimitive(locale))
                    put("publishedAt", JsonPrimitive(publishedAt))
                })
                putAll(bundleObj)
            })

            call.response.headers.append(HttpHeaders.ETag, version)
            call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=300, stale-while-revalidate=3600")
            call.respondText(responseObj.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }
    }

    // ── Versions, promote, rollback (owner-gated) ──────────────────────────────
    route("/transloom/api/projects/{id}/versions") {
        get {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val project = projectRepository.findById(projectId)
            if (project == null || project.ownerId != userId) {
                return@get call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
            }

            val active = cdnPublishRepository.getActiveVersion(projectId)
            val versions = cdnPublishRepository.listPublishes(projectId, limit = 20).map {
                VersionEntry(
                    version = it.bundleVersion,
                    publishedAt = it.publishedAt,
                    locales = it.locales,
                    active = it.bundleVersion == active
                )
            }
            call.respond(HttpStatusCode.OK, VersionsResponse(active = active, versions = versions))
        }

        post("/{version}/promote") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val version = call.parameters["version"]?.takeIf { it.isNotBlank() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid version"))
            val project = projectRepository.findById(projectId)
            if (project == null || project.ownerId != userId) {
                return@post call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
            }

            runCatching { cdnPublishService.promote(projectId, version) }
                .onSuccess { receipt ->
                    call.respond(HttpStatusCode.OK, receipt.toResponse())
                }
                .onFailure { e ->
                    when (e) {
                        is CdnPublishException -> {
                            cdnLog.warn("Promote failed for project={} version={}: {}", projectId, version, e.message)
                            call.respond(HttpStatusCode.NotFound, ApiError(e.message ?: "Promote failed"))
                        }
                        else -> {
                            cdnLog.error("Promote unexpected error for project={} version={}: {}", projectId, version, e.message)
                            call.respond(HttpStatusCode.InternalServerError, ApiError("Failed to promote version"))
                        }
                    }
                }
        }
    }

    route("/transloom/api/projects/{id}/rollback") {
        post {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val project = projectRepository.findById(projectId)
            if (project == null || project.ownerId != userId) {
                return@post call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
            }

            val receipt = runCatching { cdnPublishService.rollback(projectId) }
                .getOrElse { e ->
                    cdnLog.error("Rollback failed for project={}: {}", projectId, e.message)
                    return@post call.respond(HttpStatusCode.InternalServerError, ApiError("Rollback failed"))
                }

            if (receipt == null) {
                return@post call.respond(HttpStatusCode.Conflict, ApiError("No previous version to roll back to"))
            }
            call.respond(HttpStatusCode.OK, receipt.toResponse())
        }
    }
}

fun Route.configureCdnPublishRoute(
    projectRepository: ProjectRepository,
    cdnPublishService: CdnPublishService
) {
    route("/transloom/api/projects/{id}/publish") {
        post {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

            val project = projectRepository.findById(projectId)
            if (project == null || project.ownerId != userId) {
                return@post call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
            }

            runCatching { cdnPublishService.publish(projectId, promote = project.autoPromote) }
                .onSuccess { receipt ->
                    call.respond(HttpStatusCode.OK, receipt.toResponse())
                }
                .onFailure { e ->
                    when (e) {
                        is CdnPublishException -> {
                            cdnLog.error("CDN publish failed for project={}: {}", projectId, e.message)
                            call.respond(HttpStatusCode.BadGateway, ApiError(e.message ?: "CDN publish failed"))
                        }
                        else -> {
                            cdnLog.error("CDN publish unexpected error for project={}: {}", projectId, e.message)
                            call.respond(HttpStatusCode.InternalServerError, ApiError("Failed to publish CDN bundle"))
                        }
                    }
                }
        }
    }
}

private fun com.transloom.services.PublishReceipt.toResponse() = PublishReceiptResponse(
    publishedAt = publishedAt,
    locales = locales,
    bundleVersion = bundleVersion,
    skipped = skipped,
    skipReason = skipReason,
    promoted = promoted
)
