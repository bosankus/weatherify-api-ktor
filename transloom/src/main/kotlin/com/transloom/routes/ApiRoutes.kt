package com.transloom.routes

import com.transloom.domain.CreateProjectInput
import com.transloom.domain.PipelineRunState
import com.transloom.domain.ProjectRole
import com.transloom.domain.Translation
import com.transloom.domain.TranslationHistoryEntry
import com.transloom.repository.BillingRepository
import com.transloom.repository.GlossaryRepository
import com.transloom.repository.ProjectMembershipRepository
import com.transloom.repository.ProjectRepository
import com.transloom.repository.TranslationRepository
import com.transloom.repository.UserRepository
import com.transloom.model.*
import com.transloom.domain.UserEvent
import com.transloom.services.BillingService
import com.transloom.services.GitHubService
import com.transloom.services.CdnPublishService
import com.transloom.services.effectiveRole
import com.transloom.services.requireProjectRole
import com.transloom.services.TranslationContext
import com.transloom.services.TranslationService
import com.transloom.services.UserActivityService
import com.transloom.queue.TranslationJobQueue
import com.transloom.queue.WebhookPayload
import com.transloom.services.PipelineEventBus
import com.transloom.services.StringParserService
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
data class TranslationHistoryResponse(
    val history: List<TranslationHistoryEntry>,
    val stringKey: String
)

@Serializable
data class RetryEnqueuedResponse(
    val queued: Boolean,
    val originalRunId: String,
    val attempt: Int,
    val maxRetries: Int
)

@Serializable
data class ManualSyncResponse(
    val queued: Boolean,
    val repo: String,
    val branch: String,
    val commitShort: String
)

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
    memberships: ProjectMembershipRepository,
    cdnPublishService: CdnPublishService? = null,
    translationService: TranslationService
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

                val subscription = sub.toResponse()

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
                if (!body.prBranchPattern.isNullOrBlank() && plan == com.transloom.domain.BillingPlan.FREE) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError("Custom PR branch patterns require a Solo or Team plan.")
                    )
                }
                val validatedBranchPattern = body.prBranchPattern?.takeIf { it.isNotBlank() }?.also { pat ->
                    if (pat.length > 120 || pat.contains("..") || pat.any { c -> c == ' ' || c == '~' || c == '^' || c == ':' || c == '?' || c == '*' || c == '[' }) {
                        return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid PR branch pattern. Avoid spaces and special characters (~ ^ : ? * [ ..)"))
                    }
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
                    sharedMemoryOptIn = body.sharedMemoryOptIn,
                    prBranchPattern = validatedBranchPattern
                )
                val project = runCatching { projectRepository.create(userId, input) }.getOrElse {
                    if (it.message?.contains("E11000") == true || it.message?.contains("duplicate") == true) {
                        return@post call.respond(HttpStatusCode.Conflict, ApiError("A project for '${body.githubRepo}' already exists"))
                    }
                    apiLog.error("Project creation failed: {}", it.message)
                    return@post call.respond(HttpStatusCode.InternalServerError, ApiError("Failed to create project"))
                }

                // Materialize OWNER membership immediately so the permission helper sees the
                // creator without waiting on the startup backfill. Idempotent on retries.
                runCatching {
                    memberships.ensureOwner(project.id, userId, preflightUser?.email ?: "")
                }.onFailure { apiLog.warn("ensureOwner failed for new project={}: {}", project.id, it.message) }

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
                call.requireProjectRole(project, userId, ProjectRole.ADMIN, memberships)
                    ?: return@put
                val body = runCatching { call.receive<UpdateProjectBody>() }.getOrElse {
                    return@put call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
                }

                // Gate and validate prBranchPattern — only paid plans may set it.
                // null = no change; "" = clear (unset); non-blank = validate and set.
                val validatedBranchPattern: String? = when {
                    body.prBranchPattern == null -> null  // not in request — no change
                    body.prBranchPattern.isBlank() -> ""  // explicitly clearing the pattern
                    else -> {
                        val pat = body.prBranchPattern
                        val plan = billingService.getPlan(userId)
                        if (plan == com.transloom.domain.BillingPlan.FREE) {
                            return@put call.respond(HttpStatusCode.Forbidden, ApiError("Custom PR branch patterns require a Solo or Team plan."))
                        }
                        if (pat.length > 120 || pat.contains("..") || pat.any { c -> c == ' ' || c == '~' || c == '^' || c == ':' || c == '?' || c == '*' || c == '[' }) {
                            return@put call.respond(HttpStatusCode.BadRequest, ApiError("Invalid PR branch pattern. Avoid spaces and special characters (~ ^ : ? * [ ..)"))
                        }
                        pat
                    }
                }

                // Validate targets update: enforce language limits per plan.
                if (body.targets != null) {
                    if (body.targets.isEmpty()) {
                        return@put call.respond(HttpStatusCode.BadRequest, ApiError("At least one target language is required"))
                    }
                    if (body.targets.size > 25) {
                        return@put call.respond(HttpStatusCode.BadRequest, ApiError("Maximum 25 target languages per project"))
                    }
                    val plan = billingService.getPlan(userId)
                    if (body.targets.size > plan.maxLanguages) {
                        return@put call.respond(
                            HttpStatusCode.Forbidden,
                            ApiError("Your ${plan.displayName} plan supports up to ${plan.maxLanguages} language${if (plan.maxLanguages == 1) "" else "s"}. Upgrade to add more.")
                        )
                    }
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
                    autoApproveEnabled = body.autoApproveEnabled,
                    otaEnabled = body.otaEnabled,
                    autoPromote = body.autoPromote,
                    sharedMemoryOptIn = body.sharedMemoryOptIn,
                    prBranchPattern = validatedBranchPattern
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
                        otaEnabled = updated.otaEnabled,
                        autoPromote = updated.autoPromote,
                        webhookVerifiedAt = updated.webhookVerifiedAt?.toString(),
                        prBranchPattern = updated.prBranchPattern
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
                call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                    ?: return@get
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
                        otaEnabled = project.otaEnabled,
                        autoPromote = project.autoPromote,
                        webhookVerifiedAt = project.webhookVerifiedAt?.toString(),
                        prBranchPattern = project.prBranchPattern
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
                call.requireProjectRole(project, userId, ProjectRole.OWNER, memberships)
                    ?: return@delete
                projectRepository.delete(projectId)
                call.application.launch {
                    userActivityService.record(
                        userId, UserEvent.PROJECT_DELETED,
                        mapOf("projectId" to projectId, "repo" to project.githubRepo)
                    )
                }
                call.respond(HttpStatusCode.OK, mapOf("status" to "Project deleted", "id" to projectId))
            }

            // Round-trip export: rebuild the localized resource file for a target language
            // in the project's source format (.xml / .strings / .json). Includes auto-approved
            // and reviewer-approved strings; skips blocked/review rows so the artifact is shippable.
            //
            //   GET /transloom/api/projects/{id}/export?lang=fr&format=auto
            //
            // `format=auto` (default) picks based on the target's source file extension. Pass
            // an explicit `xml | strings | json` to override (useful when devs want a quick
            // peek in another format).
            get("/{id}/export") {
                val userId = call.userId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
                val lang = call.request.queryParameters["lang"]?.takeIf { it.isNotBlank() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Missing lang query parameter"))

                val project = projectRepository.findById(projectId)
                call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                    ?: return@get
                val target = project.targets.firstOrNull { it.code == lang }
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Language '$lang' not configured on this project"))

                val rows = translationRepository.getPublishableTranslations(projectId)
                    .filter { it.targetLanguage == lang }
                if (rows.isEmpty()) {
                    return@get call.respond(HttpStatusCode.NoContent, mapOf("message" to "Nothing to export for $lang yet"))
                }
                val entries = rows.associate { it.stringKey to it.translatedText }

                val format = (call.request.queryParameters["format"] ?: "auto").lowercase()
                val ext = when (format) {
                    "xml" -> "xml"; "strings" -> "strings"; "json" -> "json"
                    else -> target.file.substringAfterLast('.', "xml").lowercase()
                }
                val (body, contentType, filename) = when (ext) {
                    "strings" -> Triple(
                        StringParserService.mergeIosStrings("", entries),
                        ContentType.parse("text/plain; charset=utf-8"),
                        "Localizable.$lang.strings"
                    )
                    "json", "arb" -> Triple(
                        StringParserService.mergeJsonStrings("", entries),
                        ContentType.Application.Json,
                        "strings.$lang.$ext"
                    )
                    else -> Triple(
                        StringParserService.mergeAndroidXml("", entries),
                        ContentType.parse("application/xml; charset=utf-8"),
                        "strings.$lang.xml"
                    )
                }
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$filename\"")
                call.respondText(body, contentType)
            }

            get("/{id}/strings") {
                val userId = call.userId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val projectId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

                val project = projectRepository.findById(projectId)
                call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                    ?: return@get

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
                call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                    ?: return@get
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                val history = translationRepository.listHistory(projectId, stringKey, limit)
                call.respond(HttpStatusCode.OK, TranslationHistoryResponse(history = history, stringKey = stringKey))
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

                // Resolve the full set of projects the caller can review: owned projects union
                // projects where they hold an ACTIVE membership (TRANSLATOR or above).
                // Legacy owned projects that predate the membership backfill are covered by
                // listForUser; member-only projects are captured by listProjectIdsByMember.
                val (items, total, projectById) = coroutineScope {
                    // Fetch owned projects and member project IDs in parallel — both independent reads.
                    val ownedProjectsD = async { projectRepository.listForUser(userId) }
                    val memberProjectIdsD = async { memberships.listProjectIdsByMember(userId) }
                    val (ownedProjects, memberProjectIds) = ownedProjectsD.await() to memberProjectIdsD.await()
                    val allProjectIds = (ownedProjects.map { it.id } + memberProjectIds).distinct()

                    // Now fire translations queries and supplemental project fetches in parallel.
                    val itemsDeferred = async { translationRepository.listPendingReviewsForProjects(allProjectIds, limit, offset, language, statusFilter) }
                    val totalDeferred = async { translationRepository.countPendingReviewsForProjects(allProjectIds, language, statusFilter) }
                    // Fetch member-only projects (not in ownedProjects) to build the OTA map.
                    val memberOnlyIds = memberProjectIds.filterNot { id -> ownedProjects.any { it.id == id } }
                    val memberProjectsD = async {
                        memberOnlyIds.map { async { projectRepository.findById(it) } }.awaitAll().filterNotNull()
                    }
                    val allProjects = ownedProjects + memberProjectsD.await()
                    Triple(itemsDeferred.await(), totalDeferred.await(), allProjects.associateBy { it.id })
                }
                val otaByProject: Map<String, Boolean> = projectById.mapValues { it.value.otaEnabled }
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
                        commitShort = t.commitShort,
                        projectOtaEnabled = otaByProject[t.projectId] ?: false
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
                call.requireProjectRole(
                    project, userId, ProjectRole.TRANSLATOR, memberships,
                    denyStatus = HttpStatusCode.Forbidden, denyMessage = "Access denied"
                ) ?: return@post

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

                // Validate IDs are UUIDs and verify the caller has TRANSLATOR+ on every project referenced.
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
                // Reject if any referenced project is missing OR the caller lacks TRANSLATOR access on it.
                if (projects.size != projectIds.size) {
                    return@post call.respond(HttpStatusCode.Forbidden, ApiError("Access denied to one or more translations"))
                }
                val rolesOk = coroutineScope {
                    projects.map { p -> async { effectiveRole(p, userId, memberships)?.atLeast(ProjectRole.TRANSLATOR) == true } }
                        .awaitAll()
                }
                if (rolesOk.any { !it }) {
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
                call.requireProjectRole(
                    project, userId, ProjectRole.TRANSLATOR, memberships,
                    denyStatus = HttpStatusCode.Forbidden, denyMessage = "Access denied"
                ) ?: return@post

                translationRepository.reject(translationId, body.reason)
                call.respond(HttpStatusCode.OK, mapOf("status" to "rejected", "id" to translationId))
            }

            post("/{id}/hotfix") {
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val translationId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid translation id"))

                val body = runCatching { call.receive<HotfixBody>() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("newText is required"))
                }
                if (body.newText.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("newText must not be empty"))
                }

                val translation = translationRepository.getTranslation(translationId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ApiError("Translation not found"))
                val project = projectRepository.findById(translation.projectId)
                call.requireProjectRole(
                    project, userId, ProjectRole.TRANSLATOR, memberships,
                    denyStatus = HttpStatusCode.Forbidden, denyMessage = "Access denied"
                ) ?: return@post
                if (!project.otaEnabled) {
                    return@post call.respond(HttpStatusCode.Conflict, ApiError("Enable OTA for this project before hotfixing translations"))
                }
                val publishSvc = cdnPublishService
                    ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ApiError("CDN publish service not available"))

                val updated = translationRepository.hotfix(translationId, body.newText)
                if (!updated) {
                    return@post call.respond(HttpStatusCode.InternalServerError, ApiError("Hotfix update failed"))
                }

                val receipt = runCatching { publishSvc.publish(project.id, promote = project.autoPromote) }
                    .getOrElse { e ->
                        apiLog.error("Hotfix publish failed for project={}: {}", project.id, e.message)
                        return@post call.respond(
                            HttpStatusCode.OK,
                            HotfixResponse(id = translationId, translatedText = body.newText, publish = null)
                        )
                    }

                call.respond(
                    HttpStatusCode.OK,
                    HotfixResponse(
                        id = translationId,
                        translatedText = body.newText,
                        publish = PublishReceiptInline(
                            bundleVersion = receipt.bundleVersion,
                            locales = receipt.locales,
                            promoted = receipt.promoted,
                            skipped = receipt.skipped
                        )
                    )
                )
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
                call.requireProjectRole(
                    project, userId, ProjectRole.TRANSLATOR, memberships,
                    denyStatus = HttpStatusCode.Forbidden, denyMessage = "Access denied"
                ) ?: return@post
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
                call.requireProjectRole(
                    project, userId, ProjectRole.TRANSLATOR, memberships,
                    denyStatus = HttpStatusCode.Forbidden, denyMessage = "Access denied"
                ) ?: return@post
                translationRepository.unlock(translationId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "unlocked", "id" to translationId))
            }

            // Soft-fail retry: re-translate a single blocked string without rerunning the
            // whole pipeline. Useful when one row fails (Gemini transient, plural quirk) and
            // the user wants to recover it from the review portal in one click.
            post("/{id}/retry-translation") {
                val userId = call.userId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
                val translationId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid translation id"))

                val translation = translationRepository.getTranslation(translationId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ApiError("Translation not found"))
                val project = projectRepository.findById(translation.projectId)
                call.requireProjectRole(
                    project, userId, ProjectRole.TRANSLATOR, memberships,
                    denyStatus = HttpStatusCode.Forbidden, denyMessage = "Access denied"
                ) ?: return@post

                val target = project.targets.firstOrNull { it.code == translation.targetLanguage }
                    ?: return@post call.respond(HttpStatusCode.UnprocessableEntity, ApiError("Target language no longer configured on project"))

                val ctx = TranslationContext(
                    appId = project.githubRepo, appName = project.name,
                    category = project.category, tone = project.tone,
                    glossary = projectRepository.getGlossary(project.id)[target.code],
                    sourceText = translation.sourceText,
                    targetLanguage = target.name, targetRegion = target.region
                )
                val outcome = translationService.translateBatch(mapOf(translation.stringKey to ctx))[translation.stringKey]
                if (outcome == null || outcome.isFailure) {
                    apiLog.error("Translation retry failed: key={} error={}", translation.stringKey,
                        outcome?.exceptionOrNull()?.message)
                    return@post call.respond(HttpStatusCode.BadGateway,
                        ApiError("Translation service temporarily unavailable. Please try again."))
                }
                val result = outcome.getOrThrow()
                val status = if (result.flags.isNotEmpty()) "review" else "auto"
                translationRepository.upsertTranslation(
                    stringId = translation.stringId, projectId = translation.projectId,
                    ownerId = userId, stringKey = translation.stringKey, sourceText = translation.sourceText,
                    projectName = translation.projectName, targetLanguage = translation.targetLanguage,
                    targetRegion = translation.targetRegion, translatedText = result.text, status = status,
                    pipelineRunId = translation.pipelineRunId, commitShort = translation.commitShort
                )
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to status,
                    "id" to translationId,
                    "translatedText" to result.text
                ))
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
                call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                    ?: return@get

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
                call.requireProjectRole(project, userId, ProjectRole.ADMIN, memberships)
                    ?: return@post

                val body = runCatching { call.receive<GlossaryEntryBody>() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid request body"))
                }
                if (body.sourceTerm.isBlank() || body.targetTerm.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("sourceTerm and targetTerm must not be empty"))
                }
                if (body.sourceTerm.length > 200) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("sourceTerm must be 200 characters or less"))
                }
                if (body.targetTerm.length > 500) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("targetTerm must be 500 characters or less"))
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
                call.requireProjectRole(project, userId, ProjectRole.ADMIN, memberships)
                    ?: return@delete

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
                // Flush an initial comment so Cloud Run / GFE see upstream bytes immediately.
                // Without this, headers stay buffered until the first event/heartbeat and GFE 503s the connection.
                writeStringUtf8(": connected\n\n")
                flush()
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

            @Serializable data class RetryRequest(val forceTranslate: Boolean = false)
            val body = runCatching { call.receive<RetryRequest>() }.getOrDefault(RetryRequest())

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
            call.requireProjectRole(project, userId, ProjectRole.TRANSLATOR, memberships)
                ?: return@post

            val attempt = count.incrementAndGet()
            val payload = WebhookPayload(
                repositoryFullName = run.repo,
                commitHash = run.branch,
                branchName = run.branch,
                projectId = projectId,
                retriedFromRunId = runId,
                triggeredByUserId = userId,
                forceTranslate = body.forceTranslate
            )
            jobQueue.enqueueJob(payload)
            call.application.launch {
                userActivityService.record(
                    userId, UserEvent.PIPELINE_RETRIED,
                    mapOf("originalRunId" to runId, "attempt" to attempt.toString(), "repo" to run.repo)
                )
            }
            apiLog.info("Retry enqueued: originalRunId={} attempt={}/{} project={} repo={} forceTranslate={}", runId, attempt, MAX_RETRIES, projectId, run.repo, body.forceTranslate)
            call.respond(RetryEnqueuedResponse(queued = true, originalRunId = runId, attempt = attempt, maxRetries = MAX_RETRIES))
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
            call.requireProjectRole(project, userId, ProjectRole.TRANSLATOR, memberships)
                ?: return@post
            val owner = userRepository.findById(userId)
            val token = owner?.githubToken
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("No GitHub token on file. Re-authenticate."))

            val commitHash = runCatching {
                githubService.getLatestCommitHash(project.githubRepo, project.watchBranch, token)
            }.getOrElse {
                apiLog.error("Failed to resolve HEAD for repo={} branch={}: {}", project.githubRepo, project.watchBranch, it.message)
                return@post call.respond(HttpStatusCode.BadGateway,
                    ApiError("Failed to resolve branch HEAD. Check repository access and try again."))
            }

            jobQueue.enqueueJob(WebhookPayload(
                repositoryFullName = project.githubRepo,
                commitHash = commitHash,
                branchName = project.watchBranch,
                projectId = projectId,
                triggeredByUserId = userId
            ))
            apiLog.info("Manual sync enqueued: project={} repo={} branch={} commit={}",
                projectId, project.githubRepo, project.watchBranch, commitHash.take(7))
            call.respond(HttpStatusCode.Accepted, ManualSyncResponse(
                queued = true,
                repo = project.githubRepo,
                branch = project.watchBranch,
                commitShort = commitHash.take(7)
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
            call.requireProjectRole(project, userId, ProjectRole.ADMIN, memberships)
                ?: return@post
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
                .onFailure {
                    apiLog.error("Webhook install failed for repo={}: {}", project.githubRepo, it.message)
                    call.respond(HttpStatusCode.InternalServerError,
                        ApiError("Webhook installation failed. Check your repository permissions and try again."))
                }
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
                                // Fetch from the PR branch so previous commits on it aren't
                                // overwritten by merging against the base branch (watchBranch).
                                val existing = githubService.fetchFileContent(
                                    repo = project.githubRepo, branch = latestPrBranch,
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
                        // Reuse the latest run's existing prUrl (set when that run created the PR)
                        // so the activity card now shows "View pull request" instead of
                        // "No translatable strings approved".
                        val existingPrUrl = recentRuns
                            .filter { it.projectId == projectId && it.prBranch == latestPrBranch && it.prUrl != null }
                            .maxByOrNull { it.startedAt }
                            ?.prUrl
                        var cdnDetail: String? = null
                        var cdnBundleVersion: String? = null
                        var cdnLocales: List<String> = emptyList()
                        if (cdnPublishService != null) {
                            runCatching { cdnPublishService.publish(projectId, promote = project.autoPromote) }
                                .onSuccess { receipt ->
                                    apiLog.info("CDN auto-publish after review commit: project={} locales={}", projectId, receipt.locales)
                                    if (!receipt.skipped) {
                                        cdnDetail = "${receipt.locales.size} locale${if (receipt.locales.size != 1) "s" else ""} live on edge"
                                        cdnBundleVersion = receipt.bundleVersion
                                        cdnLocales = receipt.locales
                                    }
                                }
                                .onFailure { e -> apiLog.warn("CDN publish failed after review commit: project={}: {}", projectId, e.message) }
                        }
                        eventBus?.recordPostApprovalUpdate(
                            projectId = projectId,
                            ownerId = project.ownerId,
                            prUrl = existingPrUrl,
                            cdnDetail = cdnDetail,
                            cdnBundleVersion = cdnBundleVersion,
                            cdnLocales = cdnLocales
                        )
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
                    token         = githubToken,
                    branchPattern = project.prBranchPattern
                )
                apiLog.info("New follow-up PR created: {} ({} translations, {} languages)", pr.prUrl, pending.size, langCount)
                var cdnDetail: String? = null
                var cdnBundleVersion: String? = null
                var cdnLocales: List<String> = emptyList()
                if (cdnPublishService != null) {
                    runCatching { cdnPublishService.publish(projectId, promote = project.autoPromote) }
                        .onSuccess { receipt ->
                            apiLog.info("CDN auto-publish after follow-up PR: project={} locales={}", projectId, receipt.locales)
                            if (!receipt.skipped) {
                                cdnDetail = "${receipt.locales.size} locale${if (receipt.locales.size != 1) "s" else ""} live on edge"
                                cdnBundleVersion = receipt.bundleVersion
                                cdnLocales = receipt.locales
                            }
                        }
                        .onFailure { e -> apiLog.warn("CDN publish failed after follow-up PR: project={}: {}", projectId, e.message) }
                }
                eventBus?.recordPostApprovalUpdate(
                    projectId = projectId,
                    ownerId = project.ownerId,
                    prUrl = pr.prUrl,
                    cdnDetail = cdnDetail,
                    cdnBundleVersion = cdnBundleVersion,
                    cdnLocales = cdnLocales
                )
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
