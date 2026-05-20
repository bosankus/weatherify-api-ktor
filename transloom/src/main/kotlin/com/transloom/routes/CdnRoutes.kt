package com.transloom.routes

import com.transloom.model.ApiError
import com.transloom.repository.ProjectRepository
import com.transloom.services.CdnPublishException
import com.transloom.services.CdnPublishService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

private val cdnLog = LoggerFactory.getLogger("CdnRoutes")

@Serializable
data class PublishReceiptResponse(
    val publishedAt: Long,
    val locales: List<String>,
    val bundleVersion: String,
    val skipped: Boolean = false,
    val skipReason: String? = null
)

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

            runCatching { cdnPublishService.publish(projectId) }
                .onSuccess { receipt ->
                    call.respond(
                        HttpStatusCode.OK,
                        PublishReceiptResponse(
                            publishedAt = receipt.publishedAt,
                            locales = receipt.locales,
                            bundleVersion = receipt.bundleVersion,
                            skipped = receipt.skipped,
                            skipReason = receipt.skipReason
                        )
                    )
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
