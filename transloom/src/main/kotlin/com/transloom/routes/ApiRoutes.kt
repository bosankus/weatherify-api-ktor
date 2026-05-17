package com.transloom.routes

import com.transloom.domain.CreateProjectInput
import com.transloom.domain.Translation
import com.transloom.repository.GlossaryRepository
import com.transloom.repository.ProjectRepository
import com.transloom.repository.TranslationRepository
import com.transloom.repository.UserRepository
import com.transloom.model.*
import com.transloom.services.BillingService
import com.transloom.services.GitHubService
import com.transloom.services.StringParserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.UUID

private val apiLog = LoggerFactory.getLogger("ApiRoutes")

fun Route.configureApiRoutes(
    billingService: BillingService,
    githubService: GitHubService,
    projectRepository: ProjectRepository,
    userRepository: UserRepository,
    translationRepository: TranslationRepository
) {
    val glossaryRepository: GlossaryRepository by inject()

    route("/transloom/api") {

        // --- Projects ---
        route("/projects") {
            get {
                val userId = call.userId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

                val projects = projectRepository.listForUser(userId)
                val response = projects.map { p ->
                    ProjectResponse(
                        id = p.id,
                        name = p.name,
                        githubRepo = p.githubRepo,
                        watchBranch = p.watchBranch,
                        sourceFilePath = p.sourceFilePath,
                        category = p.category,
                        tone = p.tone,
                        targetCount = p.targets.size
                    )
                }
                call.respond(HttpStatusCode.OK, mapOf("projects" to response))
            }

            post {
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

                val body = runCatching { call.receive<CreateProjectBody>() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
                }

                if (body.name.isBlank() || body.githubRepo.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("name and githubRepo are required"))
                }
                if (body.targets.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("At least one target language is required"))
                }

                val currentProjectCount = projectRepository.countForUser(userId)
                val plan = billingService.getPlan(userId)
                if (currentProjectCount >= plan.maxProjects) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError("Project limit (${plan.maxProjects}) reached for ${plan.displayName} plan. Please upgrade.")
                    )
                }

                val input = CreateProjectInput(
                    name = body.name,
                    githubRepo = body.githubRepo,
                    watchBranch = body.watchBranch,
                    sourceFilePath = body.sourceFilePath,
                    category = body.category,
                    tone = body.tone,
                    targets = body.targets
                )
                val project = runCatching { projectRepository.create(userId, input) }.getOrElse {
                    if (it.message?.contains("E11000") == true || it.message?.contains("duplicate") == true) {
                        return@post call.respond(HttpStatusCode.Conflict, ApiError("A project for '${body.githubRepo}' already exists"))
                    }
                    apiLog.error("Project creation failed: {}", it.message)
                    return@post call.respond(HttpStatusCode.InternalServerError, ApiError("Failed to create project"))
                }

                val user = userRepository.findById(userId)
                val userToken = user?.githubToken
                if (userToken != null) {
                    runCatching { githubService.createWebhook(project.githubRepo, userToken) }
                        .onFailure { apiLog.warn("Webhook auto-install failed for {}: {}", project.githubRepo, it.message) }
                }

                call.respond(
                    HttpStatusCode.Created,
                    ProjectResponse(
                        id = project.id,
                        name = project.name,
                        githubRepo = project.githubRepo,
                        watchBranch = project.watchBranch,
                        sourceFilePath = project.sourceFilePath,
                        category = project.category,
                        tone = project.tone,
                        targetCount = project.targets.size
                    )
                )
            }

            put("/{id}") {
                val userId = call.userId()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

                val project = projectRepository.findById(projectId)
                if (project == null || project.ownerId != userId) {
                    return@put call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                }
                val body = runCatching { call.receive<UpdateProjectBody>() }.getOrElse {
                    return@put call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
                }

                projectRepository.update(
                    projectId = projectId,
                    name = body.name,
                    tone = body.tone,
                    category = body.category,
                    watchBranch = body.watchBranch,
                    sourceFilePath = body.sourceFilePath,
                    targets = body.targets
                )

                val updated = projectRepository.findById(projectId)
                    ?: return@put call.respond(HttpStatusCode.NotFound, ApiError("Project not found after update"))

                val owner = userRepository.findById(userId)
                val userToken = owner?.githubToken
                if (userToken != null) {
                    runCatching { githubService.ensureWebhook(updated.githubRepo, userToken) }
                        .onFailure { apiLog.warn("Webhook re-install failed on project update for {}: {}", updated.githubRepo, it.message) }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ProjectResponse(
                        id = updated.id, name = updated.name,
                        githubRepo = updated.githubRepo, watchBranch = updated.watchBranch,
                        sourceFilePath = updated.sourceFilePath, category = updated.category,
                        tone = updated.tone, targetCount = updated.targets.size
                    )
                )
            }

            delete("/{id}") {
                val userId = call.userId()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

                val project = projectRepository.findById(projectId)
                if (project == null || project.ownerId != userId) {
                    return@delete call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                }
                projectRepository.delete(projectId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "Project deleted", "id" to projectId))
            }

            get("/{id}/strings") {
                val userId = call.userId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

                val project = projectRepository.findById(projectId)
                if (project == null || project.ownerId != userId) {
                    return@get call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
                val offset = call.request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val page = translationRepository.listStringsForProject(projectId, limit, offset)
                call.respond(HttpStatusCode.OK, page)
            }
        }

        // --- Review Portal ---
        route("/review") {
            get {
                val userId = call.userId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

                val items = translationRepository.listPendingReviews(userId)
                val response = items.map { t ->
                    ReviewItemResponse(
                        id = t.id,
                        stringKey = t.stringKey,
                        sourceText = t.sourceText,
                        targetLanguage = t.targetLanguage,
                        targetRegion = t.targetRegion,
                        translatedText = t.translatedText,
                        status = t.status,
                        blockReason = t.blockReason,
                        projectId = t.projectId,
                        projectName = t.projectName
                    )
                }
                call.respond(HttpStatusCode.OK, ReviewListResponse(response, response.size))
            }

            post("/{id}/approve") {
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val translationId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid translation id"))

                val translation = translationRepository.getTranslation(translationId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ApiError("Translation not found"))
                val project = projectRepository.findById(translation.projectId)
                if (project == null || project.ownerId != userId) {
                    return@post call.respond(HttpStatusCode.Forbidden, ApiError("Access denied"))
                }

                val body = runCatching { call.receive<ApproveBody>() }.getOrElse { ApproveBody() }
                val editedText = body.editedText?.takeIf { it.isNotBlank() }
                translationRepository.approve(translationId, editedText)

                val translationForPr = if (editedText != null) translation.copy(translatedText = editedText) else translation
                launchFollowUpPr(call.application, githubService, projectRepository, userRepository, translationRepository, translationForPr)

                call.respond(HttpStatusCode.OK, mapOf("status" to "approved", "id" to translationId))
            }

            post("/{id}/reject") {
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val translationId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid translation id"))

                val body = runCatching { call.receive<RejectBody>() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("reason is required"))
                }
                if (body.reason.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("reason must not be empty"))
                }

                val translation = translationRepository.getTranslation(translationId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ApiError("Translation not found"))
                val project = projectRepository.findById(translation.projectId)
                if (project == null || project.ownerId != userId) {
                    return@post call.respond(HttpStatusCode.Forbidden, ApiError("Access denied"))
                }

                translationRepository.reject(translationId, body.reason)
                call.respond(HttpStatusCode.OK, mapOf("status" to "rejected", "id" to translationId))
            }
        }

        // --- Glossary ---
        route("/glossary/{projectId}") {
            get {
                val userId = call.userId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["projectId"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

                val project = projectRepository.findById(projectId)
                if (project == null || project.ownerId != userId) {
                    return@get call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                }

                val entries = glossaryRepository.listWithIds(projectId).map { e ->
                    GlossaryEntryResponse(id = e.id, languageCode = e.languageCode, sourceTerm = e.sourceTerm, targetTerm = e.targetTerm)
                }
                call.respond(HttpStatusCode.OK, mapOf("glossary" to entries))
            }

            post {
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["projectId"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

                val project = projectRepository.findById(projectId)
                if (project == null || project.ownerId != userId) {
                    return@post call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                }

                val body = runCatching { call.receive<GlossaryEntryBody>() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
                }
                glossaryRepository.upsert(projectId, body.languageCode, body.sourceTerm, body.targetTerm)
                call.respond(HttpStatusCode.OK, mapOf("status" to "Glossary updated"))
            }

            delete("/{entryId}") {
                val userId = call.userId()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["projectId"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
                val entryId = call.parameters["entryId"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid entry id"))

                val project = projectRepository.findById(projectId)
                if (project == null || project.ownerId != userId) {
                    return@delete call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                }

                val deleted = glossaryRepository.deactivate(entryId, projectId)
                if (deleted) call.respond(HttpStatusCode.OK, mapOf("status" to "Glossary entry removed"))
                else call.respond(HttpStatusCode.NotFound, ApiError("Entry not found"))
            }
        }

        // --- Webhook Install ---
        post("/projects/{id}/install-webhook") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

            val project = projectRepository.findById(projectId)
            if (project == null || project.ownerId != userId) {
                return@post call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
            }
            val owner = userRepository.findById(userId)
            val token = owner?.githubToken
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("No GitHub token on file. Re-authenticate."))

            runCatching { githubService.createWebhook(project.githubRepo, token) }
                .onSuccess { call.respond(HttpStatusCode.OK, mapOf("status" to "Webhook installed for ${project.githubRepo}")) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "Failed to install webhook")) }
        }
    }
}

private fun launchFollowUpPr(
    application: Application,
    githubService: GitHubService,
    projectRepository: ProjectRepository,
    userRepository: UserRepository,
    translationRepository: TranslationRepository,
    translation: Translation
) {
    application.launch {
        val project = projectRepository.findById(translation.projectId) ?: run {
            apiLog.warn("Project {} not found for follow-up PR", translation.projectId); return@launch
        }
        val owner = userRepository.findById(project.ownerId)
        val githubToken = owner?.githubToken ?: run {
            apiLog.warn("No GitHub token for user {} — cannot create follow-up PR", project.ownerId)
            return@launch
        }

        val target = project.targets.firstOrNull { it.code == translation.targetLanguage } ?: run {
            apiLog.warn("No target config for lang={} in project={}", translation.targetLanguage, project.id)
            return@launch
        }

        val maxRetries = 3
        var lastError: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val existingContent = githubService.fetchFileContent(
                    repo = project.githubRepo, branch = project.watchBranch,
                    filePath = target.file, token = githubToken
                )
                val merged = when {
                    target.file.endsWith(".xml") -> StringParserService.mergeAndroidXml(existingContent, mapOf(translation.stringKey to translation.translatedText))
                    target.file.endsWith(".strings") -> StringParserService.mergeIosStrings(existingContent, mapOf(translation.stringKey to translation.translatedText))
                    else -> return@launch
                }
                val pr = githubService.createBranchAndPr(
                    repo = project.githubRepo,
                    baseBranch = project.watchBranch,
                    files = mapOf(target.file to merged),
                    commitMessage = "chore(i18n): add approved ${translation.targetLanguage} translation for '${translation.stringKey}'",
                    prTitle = "Transloom: Approved translation — ${translation.targetLanguage}/${translation.stringKey}",
                    prBody = "Manual approval of translation for key `${translation.stringKey}` in **${translation.targetLanguage}**.\n\n> Approved via the Transloom review portal.",
                    token = githubToken
                )
                apiLog.info("Follow-up PR created after approval: {}", pr.prUrl)
                return@launch
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries) {
                    val delayMs = 1000L * (1L shl (attempt - 1))
                    apiLog.warn("Follow-up PR attempt {}/{} failed, retrying in {}ms: {}", attempt, maxRetries, delayMs, e.message)
                    delay(delayMs)
                }
            }
        }

        apiLog.error("Follow-up PR failed after {} retries for translationId={}: {}", maxRetries, translation.id, lastError?.message)
        translationRepository.revertToReview(translation.id)
        apiLog.info("Translation {} reverted to 'review' status", translation.id)
    }
}
