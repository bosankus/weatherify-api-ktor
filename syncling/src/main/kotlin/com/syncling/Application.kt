package com.syncling

import com.androidplay.core.cache.CacheRepository
import com.androidplay.core.di.coreInfraModule
import com.androidplay.core.mongo.MongoIndexer
import com.androidplay.core.queue.JobQueueRepository
import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.androidplay.core.secrets.getSecretValue
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.di.synclingIndexes
import com.syncling.di.synclingModule
import com.syncling.routes.apiToken
import com.syncling.pipeline.TranslationPipeline
import com.syncling.pipeline.buildConfigWithGlossary
import com.syncling.queue.TranslationJobQueue
import com.syncling.repository.*
import com.syncling.services.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.http.ContentType
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import com.syncling.model.ApiError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val WEBHOOK_HEAL_STALENESS = 7.days

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val log = LoggerFactory.getLogger("Application")

    val jwtSecret = getSecretValue("jwt-secret")
    val mongoUri = getSecretValue("mongo-uri")
    val encryptionKey = getSecretValue("token-encryption-key")
    val redisUrl = getSecretValue("redis-url")

    val missingSecrets = buildList {
        if (jwtSecret.isBlank()) add("jwt-secret")
        if (mongoUri.isBlank()) add("mongo-uri")
        if (encryptionKey.isBlank()) add("token-encryption-key")
    }
    if (missingSecrets.isNotEmpty()) {
        log.error("Missing required secrets: {} — refusing to start.", missingSecrets)
        kotlin.system.exitProcess(1)
    }
    if (redisUrl.isBlank()) {
        log.warn("redis-url not configured — job queue will use in-memory fallback only")
    }

    install(Koin) {
        modules(coreInfraModule(mongoUri, "transloom", redisUrl), synclingModule(encryptionKey))
    }

    val db: MongoDatabase by inject()
    runBlocking {
        runCatching { MongoIndexer.ensure(db, synclingIndexes()) }
            .onFailure { log.error("MongoDB index setup failed — DB may be unreachable: {}", it.message, it) }
    }

    install(com.syncling.plugins.RequestContextPlugin)

    // Micrometer + Prometheus. The plugin exposes JVM/HTTP metrics; PipelineMetrics owns the
    // app-specific counters on top of the same registry. /metrics is mounted in SynclingRouting.
    val meterRegistry: io.micrometer.prometheusmetrics.PrometheusMeterRegistry by inject()
    install(io.ktor.server.metrics.micrometer.MicrometerMetrics) {
        this.registry = meterRegistry
        meterBinders = listOf(
            io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics(),
            io.micrometer.core.instrument.binder.jvm.JvmGcMetrics(),
            io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics(),
            io.micrometer.core.instrument.binder.system.ProcessorMetrics(),
            io.micrometer.core.instrument.binder.system.UptimeMetrics()
        )
    }

    install(ContentNegotiation) {
        // encodeDefaults = true: otherwise response fields whose value equals
        // their Kotlin default (e.g. ProjectDetailResponse.autoPromote = true)
        // are dropped from the JSON, making the client see `undefined` and
        // mis-render toggles. ignoreUnknownKeys = true: tolerate forward-compatible
        // payloads from clients that send fields older builds don't model.
        json(Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        })
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled exception on {} {}: {}", call.request.httpMethod.value, call.request.path(), cause.message, cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Internal server error"))
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)

        val allowedOrigin = getSecretValue("allowed-origin")
        if (allowedOrigin.isBlank()) {
            log.error("ALLOWED_ORIGIN is not configured — all cross-origin requests will be blocked")
        } else {
            val uri = java.net.URI.create(allowedOrigin)
            val host = (uri.host ?: "").let { h -> if (uri.port > 0) "$h:${uri.port}" else h }
            if (host.isNotBlank() && uri.scheme != null) {
                allowHost(host, schemes = listOf(uri.scheme))
                log.info("CORS restricted to origin: {}", allowedOrigin)
            } else {
                log.error("ALLOWED_ORIGIN '{}' is malformed — cross-origin requests will be blocked", allowedOrigin)
            }
        }
    }

    // Cloud Run appends the true client IP as the *last* entry in X-Forwarded-For.
    // Using the first entry is a spoofing vector — a client can inject a fake IP
    // as the leftmost value. The rightmost entry is set by the infrastructure.
    val clientIp: (ApplicationCall) -> Any = { call ->
        call.request.headers["X-Forwarded-For"]?.split(",")?.lastOrNull()?.trim()
            ?: call.request.origin.remoteHost
    }
    install(RateLimit) {
        register(RateLimitName("auth")) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
            requestKey { clientIp(it) }
        }
        register(RateLimitName("github_webhook")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { clientIp(it) }
        }
        register(RateLimitName("razorpay_webhook")) {
            rateLimiter(limit = 30, refillPeriod = 60.seconds)
            requestKey { clientIp(it) }
        }
        // Manual sync triggers GitHub API calls — cap to 5 per minute per IP to prevent abuse
        register(RateLimitName("manual_sync")) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
            requestKey { clientIp(it) }
        }
        register(RateLimitName("bundle_fetch")) {
            rateLimiter(limit = 60, refillPeriod = 60.seconds)
            requestKey { clientIp(it) }
        }
    }

    install(io.ktor.server.plugins.defaultheaders.DefaultHeaders) {
        header("X-Frame-Options", "DENY")
        header("X-Content-Type-Options", "nosniff")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
        header("X-XSS-Protection", "0")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
    }

    install(Compression) {
        gzip {
            excludeContentType(ContentType.parse("text/event-stream"))
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Syncling API"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience("syncling-app")
                    .withIssuer("syncling-backend")
                    .build()
            )
            validate { credential ->
                val hasUserId = credential.payload.getClaim("userId")?.asString() != null
                val hasGithubId = credential.payload.getClaim("githubId")?.asLong() != null
                if (hasUserId && hasGithubId) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }
        apiToken("api-token")
    }

    val projectRepository: ProjectRepository by inject()
    val userRepository: UserRepository by inject()
    val glossaryRepository: GlossaryRepository by inject()
    val translationRepository: TranslationRepository by inject()
    // One-time backfill of denormalized fields (projectId, ownerId, stringKey, sourceText, projectName)
    // on legacy translations docs. Idempotent — only touches docs missing the sentinel projectName field.
    // Runs in background so startup latency is unaffected; reads against legacy docs return empty
    // strings for the denormalized fields until the backfill completes.
    launch {
        runCatching { translationRepository.backfillDenormalizedFields() }
            .onFailure { log.warn("Translation denormalization backfill failed: {}", it.message) }
    }
    val billingRepository: BillingRepository by inject()
    val memoryRepository: TranslationMemoryRepository by inject()
    val userActivityRepository: UserActivityRepository by inject()
    val jobQueueRepository: JobQueueRepository by inject()
    val cacheRepository: CacheRepository by inject()
    val cdnPublishRepository: CdnPublishRepository by inject()
    val notificationRepository: NotificationRepository by inject()
    val sharedMemoryRepository: SharedTranslationMemoryRepository by inject()
    val reviewerFeedbackRepository: com.syncling.repository.ReviewerFeedbackRepository by inject()
    val fuzzyMemoryService: com.syncling.services.FuzzyMemoryService by inject()
    val membershipRepository: ProjectMembershipRepository by inject()
    val pipelineRunRepository: PipelineRunRepository by inject()
    val supportTicketRepository: com.syncling.repository.SupportTicketRepository by inject()
    val apiTokenRepository: com.syncling.repository.ApiTokenRepository by inject()
    val memberUsageService: MemberUsageService by inject()
    val quotaBlockedRunRepository: com.syncling.repository.QuotaBlockedRunRepository by inject()
    val figmaCandidateRepository: com.syncling.repository.FigmaCandidateRepository by inject()
    val figmaNodeBindingRepository: com.syncling.repository.FigmaNodeBindingRepository by inject()
    val figmaPreviewRepository: com.syncling.repository.FigmaPreviewRepository by inject()
    val figmaSettingsRepository: com.syncling.repository.FigmaSettingsRepository by inject()
    val embeddingService: com.syncling.services.EmbeddingService by inject()
    val translationEmbeddingRepository: com.syncling.repository.TranslationEmbeddingRepository by inject()
    val analyticsService: AnalyticsService by inject()
    val statusService: com.syncling.services.StatusService by inject()
    // Materialize OWNER membership rows for legacy projects so the new permission
    // helper has a single code path. Idempotent — safe across restarts.
    launch {
        runCatching { backfillProjectMemberships(projectRepository, userRepository, membershipRepository) }
            .onFailure { log.warn("Membership backfill failed: {}", it.message) }
    }

    val cfAccountId = getSecretValue("cloudflare-account-id")
    val cfR2BucketName = getSecretValue("cloudflare-r2-bucket-name")
    val cfR2AccessKeyId = getSecretValue("cloudflare-r2-access-key-id")
    val cfR2SecretAccessKey = getSecretValue("cloudflare-r2-secret-access-key")
    val cfKvService = CloudflareR2Service(cfAccountId, cfR2BucketName, cfR2AccessKeyId, cfR2SecretAccessKey)
    val cdnPublishService = CdnPublishService(
        translationRepository, cfKvService, cdnPublishRepository,
        // CDN delivery (OTA) is paid-only. A project is eligible when its owner is on any non-free plan.
        isCdnEligible = { projectId ->
            val ownerId = runCatching { projectRepository.findById(projectId)?.ownerId }.getOrNull()
            ownerId != null && billingRepository.getSubscription(ownerId).plan != com.syncling.domain.BillingPlan.FREE
        }
    )

    val jobQueue = TranslationJobQueue(jobQueueRepository)
    // PipelineEventBus created first so it can be injected into UserActivityService
    // for SSE-driven onboarding step advancement.
    val pipelineEventBus = com.syncling.services.PipelineEventBus(redisUrl, pipelineRunRepository)

    val notificationService = NotificationService.fromEnv(
        host = getSecretValue("smtp-host").ifBlank { "smtp.gmail.com" },
        port = getSecretValue("smtp-port").toIntOrNull() ?: 587,
        user = getSecretValue("smtp-user"),
        password = getSecretValue("smtp-password"),
        fromName = "Syncling"
    )
    if (notificationService.isConfigured) {
        log.info("Email notifications enabled via SMTP")
    } else {
        log.info("Email notifications disabled — set smtp-user + smtp-password secrets to enable")
    }

    // Slack/Teams chat notifications are paid-only. Same eligibility pattern as CdnPublishService.
    val chatNotificationService = com.syncling.services.ChatNotificationService(
        projectRepository = projectRepository,
        isEligible = { ownerId ->
            billingRepository.getSubscription(ownerId).plan != com.syncling.domain.BillingPlan.FREE
        }
    )

    val inAppNotificationService = InAppNotificationService(notificationRepository, pipelineEventBus, chatNotificationService)

    val userActivityService = UserActivityService(
        userRepository = userRepository,
        userActivityRepository = userActivityRepository,
        billingRepository = billingRepository,
        projectRepository = projectRepository,
        eventBus = pipelineEventBus,
        notificationService = notificationService,
        inAppNotificationService = inAppNotificationService
    )
    val billingService = BillingService(billingRepository, userActivityService)
    val githubService = GitHubService()
    val quotaResumeService = QuotaResumeService(
        billingRepository = billingRepository,
        blockedRunRepository = quotaBlockedRunRepository,
        userRepository = userRepository,
        githubService = githubService,
        jobQueue = jobQueue,
        inAppNotificationService = inAppNotificationService,
        userActivityService = userActivityService
    )
    val razorpayService = RazorpayBillingService(
        billingRepository, userActivityService,
        userRepository, notificationService, inAppNotificationService,
        quotaResumeService = quotaResumeService
    )
    val lifecycleMonitor = UserLifecycleMonitor(
        userActivityService, notificationService, inAppNotificationService, razorpayService,
        quotaResumeService = quotaResumeService
    )
    lifecycleMonitor.start()
    val webhookDispatcher = RazorpayWebhookDispatcher(
        webhookSecret = getSecretValue("razorpay-webhook-secret"),
        handlers = listOf(razorpayService)
    )
    val pipelineMetrics: com.syncling.services.PipelineMetrics by inject()
    val translationService = TranslationService(memoryRepository, sharedMemoryRepository, pipelineMetrics)
    val outboundWebhookService = OutboundWebhookService()
    val figmaSyncService = com.syncling.services.FigmaSyncService(
        candidateRepository = figmaCandidateRepository,
        bindingRepository = figmaNodeBindingRepository,
        translationRepository = translationRepository,
        gitHubService = githubService,
        previewRepository = figmaPreviewRepository,
        embeddingService = embeddingService,
        embeddingRepository = translationEmbeddingRepository,
        notificationService = inAppNotificationService
    )
    val semanticChangeAnalyzer: SemanticChangeAnalyzer by inject()
    val culturalSensitivityAnalyzer: CulturalSensitivityAnalyzer by inject()
    val pipeline = TranslationPipeline(
        githubService, translationService, billingService, projectRepository, translationRepository,
        pipelineEventBus, semanticChangeAnalyzer, culturalSensitivityAnalyzer, cdnPublishService,
        sharedMemoryRepository, memberUsageService, outboundWebhookService,
        fuzzyMemoryService = fuzzyMemoryService,
        reviewerFeedbackRepository = reviewerFeedbackRepository,
        metrics = pipelineMetrics,
        blockedRunRepository = quotaBlockedRunRepository,
        visualContextProvider = figmaSyncService
    )

    jobQueue.startWorker { payload ->
        val projectUuid = runCatching { java.util.UUID.fromString(payload.projectId) }.getOrElse {
            log.error("Invalid projectId in payload: {}", payload.projectId); return@startWorker
        }
        val project = projectRepository.findById(projectUuid.toString())
        if (project == null) {
            log.warn("Project {} not found — ignoring webhook for {}", payload.projectId, payload.repositoryFullName)
            return@startWorker
        }
        val glossary = projectRepository.getGlossary(project.id)
        val config = buildConfigWithGlossary(project, glossary)
        val owner = userRepository.findById(project.ownerId)
        val githubToken = owner?.githubToken ?: run {
            log.warn("Project owner {} has no GitHub token — cannot process webhook for {}", project.ownerId, project.githubRepo)
            // Show a failed run card on the dashboard so the user sees WHY nothing happened,
            // instead of the push silently disappearing. Also fire the in-app notification.
            val errMsg = "GitHub access lost — re-connect GitHub to resume automatic translations."
            val runId = pipelineEventBus.startRun(
                project.ownerId, payload.repositoryFullName, payload.branchName,
                payload.commitHash.take(7), projectId = project.id,
                triggeredByUserId = payload.triggeredByUserId
            )
            pipelineEventBus.stepError(project.ownerId, runId, "FETCHING_STRINGS", errMsg)
            listOf("DETECTING_CHANGES", "BILLING_CHECK", "TRANSLATING", "CREATING_PR", "CDN_PUBLISH")
                .forEach { pipelineEventBus.stepSkipped(project.ownerId, runId, it) }
            pipelineEventBus.finishRun(project.ownerId, runId, error = errMsg)
            launch {
                runCatching {
                    inAppNotificationService.notifyGitHubTokenInvalid(project.ownerId, project.githubRepo, projectId = project.id)
                }
            }
            return@startWorker
        }
        // Resolve webhook commit-author email → active member userId so the run is
        // attributed in analytics. If unmatched (or already set by a manual sync/retry
        // route), leave triggeredByUserId as-is — null means "external" attribution.
        val resolvedPayload = if (payload.triggeredByUserId != null || payload.triggeredByEmail == null) {
            payload
        } else {
            val matched = runCatching {
                membershipRepository.findActiveByProjectAndEmail(project.id, payload.triggeredByEmail)
            }.getOrNull()
            payload.copy(triggeredByUserId = matched?.userId)
        }
        try {
            kotlinx.coroutines.withTimeout(10.minutes) {
                pipeline.processWebhookPayload(resolvedPayload, project, config, githubToken)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            log.error("Pipeline timed out after 10 minutes for repo={} project={}", project.githubRepo, project.id)
        }

        // Create in-app notification + optional email when pipeline produces a PR.
        // Use the most recent run for this project. Only notify if THAT run produced a PR —
        // checking for any historical prUrl would re-fire notifications for old runs when
        // the current run fails.
        val completedRun = runCatching { pipelineEventBus.recentRuns(project.ownerId) }
            .getOrElse { emptyList() }
            .firstOrNull { it.projectId == project.id }
            ?.takeIf { it.prUrl != null }
        if (completedRun != null) {
            val langDetail = completedRun.steps
                .firstOrNull { it.id == "TRANSLATING" }?.detail ?: ""
            launch {
                runCatching {
                    inAppNotificationService.notifyPipelineComplete(
                        userId = project.ownerId,
                        projectName = project.name,
                        prUrl = completedRun.prUrl!!,
                        langDetail = langDetail,
                        commitShort = completedRun.commitShort,
                        projectId = project.id
                    )
                }.onFailure { log.warn("Pipeline complete in-app notification failed project={}: {}", project.id, it.message) }
            }
            if (notificationService.isConfigured && owner.email != null) {
                launch {
                    runCatching { notificationService.sendPipelineComplete(owner, completedRun, project.name) }
                        .onFailure { log.warn("Pipeline complete email failed project={}: {}", project.id, it.message) }
                }
            }
        }
    }

    // Runs 10s after startup then every 24h. A one-shot launch would leave broken
    // webhooks unfixed until the next deploy if they break after the startup window.
    launch {
        delay(10_000)
        while (true) {
            val staleBefore = Clock.System.now() - WEBHOOK_HEAL_STALENESS
            val staleProjects = runCatching { projectRepository.listProjectsNeedingWebhookHeal(staleBefore) }.getOrElse {
                log.error("Webhook self-heal: failed to query stale projects — {}", it.message); emptyList()
            }
            if (staleProjects.isEmpty()) {
                log.info("Webhook self-heal: all webhooks verified within the last {} days", WEBHOOK_HEAL_STALENESS.inWholeDays)
            } else {
                log.info("Webhook self-heal: {} project(s) need webhook verification", staleProjects.size)

                // Fetch each unique owner's token once — many projects may share the same owner.
                val ownerTokens: Map<String, String?> = staleProjects.map { it.ownerId }.distinct()
                    .associateWith { ownerId -> userRepository.findById(ownerId)?.githubToken }

                var healed = 0
                for (project in staleProjects) {
                    val token = ownerTokens[project.ownerId]
                    if (token == null) {
                        log.debug("Webhook self-heal: skipping project {} — owner has no GitHub token", project.id)
                        continue
                    }
                    runCatching { githubService.ensureWebhook(project.githubRepo, token) }
                        .onSuccess { changed ->
                            runCatching { projectRepository.markWebhookVerified(project.id) }
                            if (changed) healed++
                        }
                        .onFailure { log.warn("Webhook self-heal failed for {}: {}", project.githubRepo, it.message) }
                    delay(500)
                }
                log.info("Webhook self-heal complete: {}/{} project(s) processed, {} webhook(s) updated",
                    staleProjects.size, staleProjects.size, healed)
            }
            delay(24.hours.inWholeMilliseconds)
        }
    }

    // Scans for pipeline runs that started more than 2 hours ago but never received a
    // finishRun call — caused by a server crash, a leaked coroutine, or an uncaught
    // exception that bypassed the normal error path. Runs 30s after startup (to let
    // the server settle), then once every 24 hours.
    launch {
        delay(30_000)
        while (true) {
            val cleaned = runCatching { pipelineEventBus.cleanupStuckRuns() }
                .onFailure { log.warn("stuckRunCleanup: unexpected error — {}", it.message) }
                .getOrElse { 0 }
            if (cleaned > 0) log.info("stuckRunCleanup: force-finished {} stuck run(s)", cleaned)
            else log.debug("stuckRunCleanup: no stuck runs found")
            delay(24.hours.inWholeMilliseconds)
        }
    }

    monitor.subscribe(ApplicationStopped) {
        // Close each resource independently — a failure in one must not prevent others from closing.
        listOf<Pair<String, () -> Unit>>(
            "job queue"              to { jobQueue.close() },
            "pipeline event bus"     to { pipelineEventBus.close() },
            "cache repository"       to { cacheRepository.close() },
            "github service"         to { githubService.close() },
            "translation service"    to { translationService.close() },
            "semantic analyzer"      to { semanticChangeAnalyzer.close() },
            "cultural analyzer"      to { culturalSensitivityAnalyzer.close() },
            "razorpay service"       to { razorpayService.close() },
            "lifecycle monitor"      to { lifecycleMonitor.stop() },
            "cloudflare r2 service"  to { cfKvService.close() },
            "outbound webhook service" to { outboundWebhookService.close() },
            "chat notification service" to { chatNotificationService.close() },
            "figma sync service"     to { figmaSyncService.close() },
            "embedding service"      to { embeddingService.close() }
        ).forEach { (name, closer) ->
            runCatching(closer).onFailure { log.error("Failed to close {}: {}", name, it.message) }
        }
        log.info("All resources closed on application stop")
    }

    installSynclingRoutes(SynclingDeps(
        jwtSecret = jwtSecret,
        jobQueue = jobQueue,
        db = db,
        jobQueueRepository = jobQueueRepository,
        webhookDispatcher = webhookDispatcher,
        projectRepository = projectRepository,
        userRepository = userRepository,
        billingRepository = billingRepository,
        translationRepository = translationRepository,
        glossaryRepository = glossaryRepository,
        notificationRepository = notificationRepository,
        membershipRepository = membershipRepository,
        cdnPublishRepository = cdnPublishRepository,
        billingService = billingService,
        razorpayService = razorpayService,
        githubService = githubService,
        userActivityService = userActivityService,
        pipelineEventBus = pipelineEventBus,
        cdnPublishService = cdnPublishService,
        translationService = translationService,
        analyticsService = analyticsService,
        statusService = statusService,
        notificationService = notificationService,
        inAppNotificationService = inAppNotificationService,
        pipelineRunRepository = pipelineRunRepository,
        supportTicketRepository = supportTicketRepository,
        apiTokenRepository = apiTokenRepository,
        reviewerFeedbackRepository = reviewerFeedbackRepository,
        meterRegistry = meterRegistry,
        quotaBlockedRunRepository = quotaBlockedRunRepository,
        quotaResumeService = quotaResumeService,
        figmaSyncService = figmaSyncService,
        figmaCandidateRepository = figmaCandidateRepository,
        figmaPreviewRepository = figmaPreviewRepository,
        figmaSettingsRepository = figmaSettingsRepository,
        chatNotificationService = chatNotificationService,
    ))
}
