package com.transloom.routes

import com.transloom.domain.CreateProjectInput
import com.transloom.domain.PipelineRunState
import com.transloom.domain.Translation
import com.transloom.repository.BillingRepository
import com.transloom.repository.GlossaryRepository
import com.transloom.repository.ProjectRepository
import com.transloom.repository.TranslationRepository
import com.transloom.repository.UserRepository
import com.transloom.model.*
import com.transloom.domain.UserEvent
import com.transloom.services.BillingService
import com.transloom.services.GitHubService
import com.transloom.services.CdnPublishService
import com.transloom.services.UserActivityService
import com.transloom.queue.TranslationJobQueue
import com.transloom.queue.WebhookPayload
import com.transloom.services.PipelineEventBus
import com.transloom.services.StringParserService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID

private val apiLog = LoggerFactory.getLogger("ApiRoutes")

private const val MAX_RETRIES = 3
// TTL-bounded retry tracking — entries older than 30 min are evicted on each access
// so the map never grows unbounded across thousands of pipeline runs.
private const val RETRY_TTL_MS = 1_800_000L
private data class RetryRecord(val count: java.util.concurrent.atomic.AtomicInteger, val createdAt: Long = System.currentTimeMillis())
private val retryAttempts = java.util.concurrent.ConcurrentHashMap<String, RetryRecord>()
private fun retryCount(runId: String): java.util.concurrent.atomic.AtomicInteger {
    val cutoff = System.currentTimeMillis() - RETRY_TTL_MS
    retryAttempts.entries.removeIf { it.value.createdAt < cutoff }
    return retryAttempts.getOrPut(runId) { RetryRecord(java.util.concurrent.atomic.AtomicInteger(0)) }.count
}

@Serializable
data class BootstrapResponse(
    val onboarding: OnboardingStateResponse,
    val stats: DashboardStats,
    val subscription: SubscriptionResponse,
    val usage: UsageResponse,
    val runs: List<PipelineRunState>
)

fun Route.configureApiRoutes(
    billingService: BillingService,
    billingRepository: BillingRepository,
    githubService: GitHubService,
    projectRepository: ProjectRepository,
    userRepository: UserRepository,
    translationRepository: TranslationRepository,
    pipelineEventBus: PipelineEventBus,
    jobQueue: TranslationJobQueue,
    glossaryRepository: GlossaryRepository,
    userActivityService: UserActivityService,
    cdnPublishService: CdnPublishService? = null
) {

    route("/transloom/api") {

        // Single endpoint that replaces the 5 separate page-load calls. All queries run in
        // parallel (9 concurrent coroutines) — total latency equals the slowest single query.
        get("/me/bootstrap") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            coroutineScope {
                val userD          = async { userRepository.findById(userId) }
                val subD           = async { billingRepository.getSubscription(userId) }
                val projectCountD  = async { projectRepository.countForUser(userId) }
                val statusCountsD  = async { translationRepository.countByStatus(userId) }
                val totalStringsD  = async { translationRepository.totalStringsTranslated(userId) }
                val activeLangsD   = async { translationRepository.activeLanguageCount(userId) }
                val usageD         = async { billingRepository.getUsage(userId) }
                val historyD       = async { billingRepository.getHistoricalUsage(userId) }
                val runsD          = async { pipelineEventBus.recentRuns(userId) }

                val user         = userD.await()
                val sub          = subD.await()
                val projectCount = projectCountD.await()
                val statusCounts = statusCountsD.await()
                val totalStrings = totalStringsD.await()
                val activeLangs  = activeLangsD.await()
                val usage        = usageD.await()
                val history      = historyD.await()
                val runs         = runsD.await()

                val plan = sub.plan

                val onboarding = OnboardingStateResponse(
                    step = user?.onboardingStep?.name ?: "SIGNED_UP",
                    dismissed = user?.onboardingDismissedAt != null,
                    completed = user?.onboardingStep?.name == "COMPLETED",
                    plan = plan.name,
                    inTrial = sub.inTrial,
                    trialLimitHit = sub.inTrial && sub.limitHitAt != null,
                    hasProject = projectCount > 0,
                    showTour = user?.onboardingStep?.name != "COMPLETED" && user?.onboardingDismissedAt == null
                )

                val stats = DashboardStats(
                    totalStringsTranslated = totalStrings,
                    pendingReview = statusCounts["review"] ?: 0,
                    blockedCount = statusCounts["blocked"] ?: 0,
                    activeLanguages = activeLangs,
                    totalProjects = projectCount,
                    currentPlan = plan.name,
                    currentPlanDisplay = plan.displayName
                )

                val now = kotlinx.datetime.Clock.System.now()
                val sevenDays = kotlin.time.Duration.parse("7d")
                val trialEndsOn = if (sub.inTrial && sub.startedAt != null)
                    (sub.startedAt + sevenDays).toLocalDateTime(TimeZone.UTC).date.toString()
                else null
                val daysUntilRenewal = when {
                    sub.inTrial && sub.startedAt != null ->
                        ((sub.startedAt + sevenDays) - now).inWholeDays.toInt().coerceAtLeast(0)
                    sub.currentPeriodEnd != null -> (sub.currentPeriodEnd - now).inWholeDays.toInt().coerceAtLeast(0)
                    else -> null
                }
                val subscription = SubscriptionResponse(
                    plan = plan.name,
                    displayName = plan.displayName,
                    monthlyPricePaise = plan.monthlyPricePaise,
                    stringLimit = plan.stringLimit,
                    maxProjects = if (plan.maxProjects == Int.MAX_VALUE) -1 else plan.maxProjects,
                    cancelAtPeriodEnd = sub.cancelAtPeriodEnd,
                    currentPeriodEnd = sub.currentPeriodEnd?.toLocalDateTime(TimeZone.UTC)?.date?.toString(),
                    trialLimitHit = sub.inTrial && sub.limitHitAt != null,
                    inTrial = sub.inTrial,
                    trialEndsOn = trialEndsOn,
                    daysUntilRenewal = daysUntilRenewal
                )

                val usageResp = UsageResponse(
                    stringsTranslated = usage.stringsTranslated,
                    stringLimit = plan.stringLimit,
                    projectsUsed = usage.projectsUsed,
                    projectLimit = if (plan.maxProjects == Int.MAX_VALUE) null else plan.maxProjects,
                    history = history.map { HistoryEntry(it.yearMonth, it.stringsTranslated) }
                )

                call.respond(HttpStatusCode.OK, BootstrapResponse(onboarding, stats, subscription, usageResp, runs))
            }
        }

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
                        sourceFilePaths = p.sourceFilePaths,
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
                if (body.name.length > 100) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("Project name must be 100 characters or less"))
                }
                if (body.githubRepo.length > 200 || !body.githubRepo.contains("/")) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("githubRepo must be in owner/repo format"))
                }
                if (body.targets.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("At least one target language is required"))
                }
                if (body.targets.size > 25) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("Maximum 25 target languages per project"))
                }

                // Fetch count, plan, and user token in parallel — all are independent reads.
                val (currentProjectCount, plan, preflightUser) = coroutineScope {
                    val count = async { projectRepository.countForUser(userId) }
                    val plan  = async { billingService.getPlan(userId) }
                    val user  = async { userRepository.findById(userId) }
                    Triple(count.await(), plan.await(), user.await())
                }
                if (currentProjectCount >= plan.maxProjects) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError("Project limit (${plan.maxProjects}) reached for ${plan.displayName} plan. Please upgrade.")
                    )
                }
                if (preflightUser?.githubToken.isNullOrBlank()) {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        StructuredApiError(
                            error = "GitHub access is required to connect a repository.",
                            code = "GITHUB_REAUTH_REQUIRED",
                            actionHint = "Reconnect your GitHub account to continue.",
                            reauthUrl = "/transloom/auth/github"
                        )
                    )
                }

                // Validate the repo exists and the token has access before writing to DB.
                val repoError = preflightUser?.githubToken?.let {
                    githubService.validateRepo(body.githubRepo, it)
                }
                if (repoError != null) {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        StructuredApiError(error = repoError, code = "REPO_NOT_ACCESSIBLE")
                    )
                }

                val input = CreateProjectInput(
                    name = body.name,
                    githubRepo = body.githubRepo,
                    watchBranch = body.watchBranch,
                    sourceFilePaths = body.sourceFilePaths.filter { it.isNotBlank() }.ifEmpty { listOf("values/strings.xml") },
                    category = body.category,
                    tone = body.tone,
                    targets = body.targets,
                    culturalSensitivityEnabled = false,
                    sharedMemoryOptIn = body.sharedMemoryOptIn
                )
                val project = runCatching { projectRepository.create(userId, input) }.getOrElse {
                    if (it.message?.contains("E11000") == true || it.message?.contains("duplicate") == true) {
                        return@post call.respond(HttpStatusCode.Conflict, ApiError("A project for '${body.githubRepo}' already exists"))
                    }
                    apiLog.error("Project creation failed: {}", it.message)
                    return@post call.respond(HttpStatusCode.InternalServerError, ApiError("Failed to create project"))
                }

                call.application.launch {
                    userActivityService.record(
                        userId, UserEvent.PROJECT_CREATED,
                        mapOf("projectId" to project.id, "repo" to project.githubRepo)
                    )
                }

                val userToken = preflightUser!!.githubToken!!
                runCatching { githubService.createWebhook(project.githubRepo, userToken) }
                    .onSuccess {
                        runCatching { projectRepository.markWebhookVerified(project.id) }
                        call.application.launch {
                            userActivityService.record(
                                userId, UserEvent.WEBHOOK_INSTALLED,
                                mapOf("projectId" to project.id, "repo" to project.githubRepo)
                            )
                        }
                    }
                    .onFailure { apiLog.warn("Webhook auto-install failed for {}: {}", project.githubRepo, it.message) }

                call.respond(
                    HttpStatusCode.Created,
                    ProjectResponse(
                        id = project.id,
                        name = project.name,
                        githubRepo = project.githubRepo,
                        watchBranch = project.watchBranch,
                        sourceFilePaths = project.sourceFilePaths,
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
                    sourceFilePaths = body.sourceFilePaths?.filter { it.isNotBlank() }?.ifEmpty { null },
                    targets = body.targets,
                    culturalSensitivityEnabled = body.culturalSensitivityEnabled,
                    autoApproveEnabled = body.autoApproveEnabled
                )

                val updated = projectRepository.findById(projectId)
                    ?: return@put call.respond(HttpStatusCode.NotFound, ApiError("Project not found after update"))

                val owner = userRepository.findById(userId)
                val userToken = owner?.githubToken
                if (userToken != null) {
                    runCatching { githubService.ensureWebhook(updated.githubRepo, userToken) }
                        .onSuccess { runCatching { projectRepository.markWebhookVerified(updated.id) } }
                        .onFailure { apiLog.warn("Webhook re-install failed on project update for {}: {}", updated.githubRepo, it.message) }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ProjectDetailResponse(
                        id = updated.id, name = updated.name,
                        githubRepo = updated.githubRepo, watchBranch = updated.watchBranch,
                        sourceFilePaths = updated.sourceFilePaths, category = updated.category,
                        tone = updated.tone, targets = updated.targets,
                        culturalSensitivityEnabled = updated.culturalSensitivityEnabled,
                        autoApproveEnabled = updated.autoApproveEnabled,
                        sharedMemoryOptIn = updated.sharedMemoryOptIn,
                        webhookVerifiedAt = updated.webhookVerifiedAt?.toString()
                    )
                )
            }

            get("/{id}") {
                val userId = call.userId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

                val project = projectRepository.findById(projectId)
                if (project == null || project.ownerId != userId) {
                    return@get call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                }
                call.respond(
                    HttpStatusCode.OK,
                    ProjectDetailResponse(
                        id = project.id,
                        name = project.name,
                        githubRepo = project.githubRepo,
                        watchBranch = project.watchBranch,
                        sourceFilePaths = project.sourceFilePaths,
                        category = project.category,
                        tone = project.tone,
                        targets = project.targets,
                        culturalSensitivityEnabled = project.culturalSensitivityEnabled,
                        autoApproveEnabled = project.autoApproveEnabled,
                        sharedMemoryOptIn = project.sharedMemoryOptIn,
                        webhookVerifiedAt = project.webhookVerifiedAt?.toString()
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
                call.application.launch {
                    userActivityService.record(
                        userId, UserEvent.PROJECT_DELETED,
                        mapOf("projectId" to projectId, "repo" to project.githubRepo)
                    )
                }
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

            get("/{id}/strings/{key}/history") {
                val userId = call.userId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
                val stringKey = call.parameters["key"]
                    ?.takeIf { it.isNotBlank() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Missing string key"))

                val project = projectRepository.findById(projectId)
                if (project == null || project.ownerId != userId) {
                    return@get call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                val history = translationRepository.listHistory(projectId, stringKey, limit)
                call.respond(HttpStatusCode.OK, mapOf("history" to history, "stringKey" to stringKey))
            }
        }

        // --- Review Portal ---
        route("/review") {
            get {
                val userId = call.userId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
                val offset = call.request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val language = call.request.queryParameters["language"]?.takeIf { it.isNotBlank() }
                val statusFilter = call.request.queryParameters["status"]?.takeIf { it.isNotBlank() }

                val (items, total) = coroutineScope {
                    val itemsDeferred = async { translationRepository.listPendingReviews(userId, limit, offset, language, statusFilter) }
                    val totalDeferred = async { translationRepository.countPendingReviews(userId, language, statusFilter) }
                    itemsDeferred.await() to totalDeferred.await()
                }
                val response = items.map { t ->
                    ReviewItemResponse(
                        id = t.id,
                        stringKey = t.stringKey,
                        sourceText = t.sourceText,
                        targetLanguage = t.targetLanguage,
                        targetRegion = t.targetRegion,
                        translatedText = t.translatedText,
                        previousTranslatedText = t.previousTranslatedText,
                        status = t.status,
                        blockReason = t.blockReason,
                        lockedAt = t.lockedAt,
                        lockedBy = t.lockedBy,
                        projectId = t.projectId,
                        projectName = t.projectName,
                        pipelineRunId = t.pipelineRunId,
                        commitShort = t.commitShort
                    )
                }
                call.respond(HttpStatusCode.OK, ReviewListResponse(response, total))
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

                launchFollowUpPr(call.application, githubService, projectRepository, userRepository, translationRepository, translation.projectId, cdnPublishService, eventBus = pipelineEventBus)

                call.respond(HttpStatusCode.OK, mapOf("status" to "approved", "id" to translationId))
            }

            post("/batch-approve") {
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

                val body = runCatching { call.receive<BatchApproveBody>() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
                }
                if (body.ids.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("ids must not be empty"))
                }
                if (body.ids.size > 200) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("Batch size cannot exceed 200"))
                }

                // Validate IDs are UUIDs and verify the caller owns all projects referenced.
                val validIds = body.ids.mapNotNull { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                if (validIds.size != body.ids.size) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("One or more invalid translation ids"))
                }

                // Single aggregation pass instead of N individual getTranslation() calls.
                val translations = translationRepository.listByIds(validIds)
                val projectIds = translations.map { it.projectId }.distinct()
                val projects = coroutineScope {
                    projectIds.map { id -> async { projectRepository.findById(id) } }.awaitAll().filterNotNull()
                }
                if (projects.any { it.ownerId != userId }) {
                    return@post call.respond(HttpStatusCode.Forbidden, ApiError("Access denied to one or more translations"))
                }

                val approved = translationRepository.approveMany(validIds)

                // Trigger a follow-up PR per affected project (existing claim+PR logic).
                projectIds.forEach { projectId ->
                    launchFollowUpPr(call.application, githubService, projectRepository, userRepository, translationRepository, projectId, cdnPublishService, eventBus = pipelineEventBus)
                }

                apiLog.info("Batch approve: userId={} approved={} of {}", userId, approved, validIds.size)
                call.respond(HttpStatusCode.OK, mapOf("approved" to approved, "total" to validIds.size))
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

            post("/{id}/lock") {
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
                val locked = translationRepository.lock(translationId, userId)
                if (locked) call.respond(HttpStatusCode.OK, mapOf("status" to "locked", "id" to translationId))
                else call.respond(HttpStatusCode.Conflict, ApiError("Translation is already locked"))
            }

            post("/{id}/unlock") {
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
                translationRepository.unlock(translationId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "unlocked", "id" to translationId))
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

        // --- Pipeline Activity ---

        // Returns last 20 pipeline runs for the authenticated user (page-load snapshot).
        get("/pipeline/runs") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            call.respond(mapOf("runs" to pipelineEventBus.recentRuns(userId)))
        }

        // SSE stream of real-time pipeline events. EventSource can't send Authorization headers,
        // so clients must use fetch() with Authorization: Bearer — see connectPipelineSSE() in DASHBOARD_JS.
        get("/pipeline/events") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            call.response.header(HttpHeaders.CacheControl, "no-cache, no-transform")
            call.response.header(HttpHeaders.Connection, "keep-alive")
            call.response.header("X-Accel-Buffering", "no")
            call.respondBytesWriter(contentType = ContentType.parse("text/event-stream; charset=utf-8")) {
                coroutineScope {
                    val heartbeat = launch {
                        while (isActive) {
                            delay(25_000)
                            try {
                                writeStringUtf8(": ping\n\n")
                                flush()
                            } catch (_: Exception) {
                                // Channel closed (client disconnected or Cloud Run cut the connection)
                                cancel()
                                break
                            }
                        }
                    }
                    try {
                        pipelineEventBus.eventsFor(userId).collect { json ->
                            try {
                                writeStringUtf8("data: $json\n\n")
                                flush()
                            } catch (_: Exception) {
                                cancel()
                                return@collect
                            }
                        }
                    } catch (_: CancellationException) {
                        // Normal shutdown when heartbeat detected disconnect
                    } finally {
                        heartbeat.cancel()
                    }
                }
            }
        }

        // Retrigger a failed pipeline run using the same project + branch.
        post("/pipeline/runs/{runId}/retry") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val runId = call.parameters["runId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Missing runId"))

            val count = retryCount(runId)
            if (count.get() >= MAX_RETRIES) {
                return@post call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiError("Maximum $MAX_RETRIES retries reached for this run. Fix the underlying issue before trying again.")
                )
            }

            val run = pipelineEventBus.recentRuns(userId).find { it.runId == runId }
                ?: return@post call.respond(HttpStatusCode.NotFound, ApiError("Run not found"))

            val projectId = run.projectId
                ?: return@post call.respond(HttpStatusCode.UnprocessableEntity, ApiError("Run has no project reference — cannot retry"))

            // Verify the user still owns the project before requeuing
            val project = projectRepository.findById(projectId)
            if (project == null || project.ownerId != userId) {
                return@post call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
            }

            val attempt = count.incrementAndGet()
            val payload = WebhookPayload(
                repositoryFullName = run.repo,
                commitHash = run.branch,
                branchName = run.branch,
                projectId = projectId,
                retriedFromRunId = runId
            )
            jobQueue.enqueueJob(payload)
            call.application.launch {
                userActivityService.record(
                    userId, UserEvent.PIPELINE_RETRIED,
                    mapOf("originalRunId" to runId, "attempt" to attempt.toString(), "repo" to run.repo)
                )
            }
            apiLog.info("Retry enqueued: originalRunId={} attempt={}/{} project={} repo={}", runId, attempt, MAX_RETRIES, projectId, run.repo)
            call.respond(mapOf("queued" to true, "originalRunId" to runId, "attempt" to attempt, "maxRetries" to MAX_RETRIES))
        }

        // --- Manual Sync (rate-limited: 5/min per IP) ---
        rateLimit(RateLimitName("manual_sync")) {
        post("/projects/{id}/sync") {
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

            val commitHash = runCatching {
                githubService.getLatestCommitHash(project.githubRepo, project.watchBranch, token)
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadGateway, ApiError("Could not resolve HEAD of '${project.watchBranch}': ${it.message}"))
            }

            jobQueue.enqueueJob(WebhookPayload(
                repositoryFullName = project.githubRepo,
                commitHash = commitHash,
                branchName = project.watchBranch,
                projectId = projectId
            ))
            apiLog.info("Manual sync enqueued: project={} repo={} branch={} commit={}",
                projectId, project.githubRepo, project.watchBranch, commitHash.take(7))
            call.respond(HttpStatusCode.Accepted, mapOf(
                "queued" to true,
                "repo" to project.githubRepo,
                "branch" to project.watchBranch,
                "commitShort" to commitHash.take(7)
            ))
        }} // end rateLimit + post

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
                .onSuccess {
                    runCatching { projectRepository.markWebhookVerified(project.id) }
                    call.application.launch {
                        userActivityService.record(
                            userId, UserEvent.WEBHOOK_INSTALLED,
                            mapOf("projectId" to project.id, "repo" to project.githubRepo)
                        )
                    }
                    call.respond(HttpStatusCode.OK, mapOf("status" to "Webhook installed for ${project.githubRepo}"))
                }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "Failed to install webhook")) }
        }
    }
}

// 2s delay collects simultaneous approvals into one PR. Atomic claim (approved → auto) prevents duplicate PRs.
private fun launchFollowUpPr(
    application: Application,
    githubService: GitHubService,
    projectRepository: ProjectRepository,
    userRepository: UserRepository,
    translationRepository: TranslationRepository,
    projectId: String,
    cdnPublishService: CdnPublishService? = null,
    eventBus: PipelineEventBus? = null
) {
    application.launch {
        delay(2_000L)

        val pending = translationRepository.getApprovedForProject(projectId)
        if (pending.isEmpty()) return@launch

        val claimed = translationRepository.claimApproved(pending.map { it.id })
        if (claimed == 0) {
            apiLog.debug("Follow-up PR: lost claim race for project={}, bailing", projectId)
            return@launch
        }

        val project = projectRepository.findById(projectId) ?: run {
            apiLog.warn("Follow-up PR: project {} not found", projectId)
            pending.forEach { translationRepository.revertToReview(it.id) }
            return@launch
        }
        val owner = userRepository.findById(project.ownerId)
        val githubToken = owner?.githubToken ?: run {
            apiLog.warn("Follow-up PR: no GitHub token for project {}", projectId)
            pending.forEach { translationRepository.revertToReview(it.id) }
            return@launch
        }

        val fileTranslations = mutableMapOf<String, MutableMap<String, String>>()
        for (t in pending) {
            val target = project.targets.firstOrNull { it.code == t.targetLanguage } ?: continue
            fileTranslations.getOrPut(target.file) { mutableMapOf() }[t.stringKey] = t.translatedText
        }
        if (fileTranslations.isEmpty()) return@launch

        val stringCount = pending.map { it.stringKey }.distinct().size
        val langCount   = pending.map { it.targetLanguage }.distinct().size

        // ── Try to add a commit to the most recent open PR branch for this project ──
        val recentRuns = if (eventBus != null) runCatching { eventBus.recentRuns(project.ownerId) }.getOrElse { emptyList() } else emptyList()
        val latestPrBranch = recentRuns
            .filter { it.projectId == projectId && it.prBranch != null }
            .maxByOrNull { it.startedAt }
            ?.prBranch

        if (latestPrBranch != null) {
            val maxRetries = 3
            var lastError: Exception? = null
            for (attempt in 1..maxRetries) {
                try {
                    val updatedFiles: Map<String, String> = coroutineScope {
                        fileTranslations.entries.map { (filePath, translations) ->
                            async {
                                val existing = githubService.fetchFileContent(
                                    repo = project.githubRepo, branch = project.watchBranch,
                                    filePath = filePath, token = githubToken
                                )
                                val merged = when {
                                    filePath.endsWith(".xml")     -> StringParserService.mergeAndroidXml(existing, translations)
                                    filePath.endsWith(".strings") -> StringParserService.mergeIosStrings(existing, translations)
                                    filePath.endsWith(".json") || filePath.endsWith(".arb") ->
                                        StringParserService.mergeJsonStrings(existing, translations)
                                    else -> return@async null
                                }
                                filePath to merged
                            }
                        }.awaitAll().filterNotNull().toMap()
                    }

                    val added = githubService.addCommitToBranch(
                        repo          = project.githubRepo,
                        branchName    = latestPrBranch,
                        files         = updatedFiles,
                        commitMessage = "chore(i18n): add $stringCount reviewed translation${if (stringCount != 1) "s" else ""} for $langCount language${if (langCount != 1) "s" else ""} (approved via Transloom portal)",
                        token         = githubToken
                    )

                    if (added) {
                        apiLog.info("Review commit pushed to existing PR branch={} ({} translations, {} languages)", latestPrBranch, pending.size, langCount)
                        if (cdnPublishService != null) {
                            runCatching { cdnPublishService.publish(projectId) }
                                .onSuccess { receipt -> apiLog.info("CDN auto-publish after review commit: project={} locales={}", projectId, receipt.locales) }
                                .onFailure { e -> apiLog.warn("CDN publish failed after review commit: project={}: {}", projectId, e.message) }
                        }
                        return@launch
                    }
                    // Branch no longer exists — fall through to create a new PR
                    apiLog.info("PR branch {} not found — will create a new PR for project={}", latestPrBranch, projectId)
                    break
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < maxRetries) {
                        val delayMs = 1000L * (1L shl (attempt - 1))
                        apiLog.warn("Review commit attempt {}/{} failed, retrying in {}ms: {}", attempt, maxRetries, delayMs, e.message)
                        delay(delayMs)
                    }
                }
            }
            if (lastError != null) {
                apiLog.warn("Review commit to existing branch failed after retries, falling back to new PR for project={}", projectId)
            }
        }

        // ── Fall back: create a new PR ──────────────────────────────────────────
        val maxRetries = 3
        var lastError: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val updatedFiles: Map<String, String> = coroutineScope {
                    fileTranslations.entries.map { (filePath, translations) ->
                        async {
                            val existing = githubService.fetchFileContent(
                                repo = project.githubRepo, branch = project.watchBranch,
                                filePath = filePath, token = githubToken
                            )
                            val merged = when {
                                filePath.endsWith(".xml")     -> StringParserService.mergeAndroidXml(existing, translations)
                                filePath.endsWith(".strings") -> StringParserService.mergeIosStrings(existing, translations)
                                filePath.endsWith(".json") || filePath.endsWith(".arb") ->
                                    StringParserService.mergeJsonStrings(existing, translations)
                                else -> return@async null
                            }
                            filePath to merged
                        }
                    }.awaitAll().filterNotNull().toMap()
                }

                val pr = githubService.createBranchAndPr(
                    repo          = project.githubRepo,
                    baseBranch    = project.watchBranch,
                    files         = updatedFiles,
                    commitMessage = "chore(i18n): add $stringCount reviewed translation${if (stringCount != 1) "s" else ""} for $langCount language${if (langCount != 1) "s" else ""}",
                    prTitle       = "Transloom: $stringCount reviewed translation${if (stringCount != 1) "s" else ""} · $langCount language${if (langCount != 1) "s" else ""}",
                    prBody        = buildApprovalPrBody(pending),
                    token         = githubToken
                )
                apiLog.info("New follow-up PR created: {} ({} translations, {} languages)", pr.prUrl, pending.size, langCount)
                if (cdnPublishService != null) {
                    runCatching { cdnPublishService.publish(projectId) }
                        .onSuccess { receipt -> apiLog.info("CDN auto-publish after follow-up PR: project={} locales={}", projectId, receipt.locales) }
                        .onFailure { e -> apiLog.warn("CDN publish failed after follow-up PR: project={}: {}", projectId, e.message) }
                }
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

        apiLog.error("Follow-up PR failed after {} retries for project={}: {}", maxRetries, projectId, lastError?.message)
        pending.forEach { translationRepository.revertToReview(it.id) }
        apiLog.info("{} translations reverted to 'review' for project={}", pending.size, projectId)
    }
}

private fun buildApprovalPrBody(translations: List<Translation>): String {
    val rows = translations.groupBy { it.targetLanguage }.entries
        .joinToString("\n") { (lang, items) ->
            val keys = items.joinToString(", ") { "`${it.stringKey}`" }
            "| ${lang.uppercase()} | ${items.size} | $keys |"
        }
    return """
        ## Transloom: Approved Translations

        | Language | Strings | Keys |
        |---|---|---|
        $rows

        > Manually reviewed and approved via the Transloom review portal.

        *Generated by [Transloom](https://transloom.dev)*
    """.trimIndent()
}
