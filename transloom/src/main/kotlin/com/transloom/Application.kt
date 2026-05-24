package com.transloom

import com.androidplay.core.cache.CacheRepository
import com.androidplay.core.di.coreInfraModule
import com.androidplay.core.mongo.MongoIndexer
import com.androidplay.core.queue.JobQueueRepository
import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.androidplay.core.secrets.getSecretValue
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.di.transloomIndexes
import com.transloom.di.transloomModule
import com.transloom.pipeline.TranslationPipeline
import com.transloom.pipeline.buildConfigWithGlossary
import com.transloom.queue.TranslationJobQueue
import com.transloom.repository.*
import com.transloom.services.*
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
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private val WEBHOOK_HEAL_STALENESS = 7.days

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val log = LoggerFactory.getLogger("Application")

    val jwtSecret = getSecretValue("jwt-secret")
    val mongoUri = getSecretValue("mongo-uri")
    val encryptionKey = getSecretValue("token-encryption-key")
    val redisUrl = getSecretValue("redis-url")

    install(Koin) {
        modules(coreInfraModule(mongoUri, "transloom", redisUrl), transloomModule(encryptionKey))
    }

    val db: MongoDatabase by inject()
    runBlocking { MongoIndexer.ensure(db, transloomIndexes()) }

    install(ContentNegotiation) {
        json()
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

    val clientIp: (ApplicationCall) -> Any = { call ->
        call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
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

    install(Compression) { gzip() }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Transloom API"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience("transloom-app")
                    .withIssuer("transloom-backend")
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

    val cfAccountId = getSecretValue("cloudflare-account-id")
    val cfNamespaceId = getSecretValue("cloudflare-kv-namespace-id")
    val cfApiToken = getSecretValue("cloudflare-api-token")
    val cfKvService = CloudflareKvService(cfAccountId, cfNamespaceId, cfApiToken)
    val cdnPublishService = CdnPublishService(translationRepository, cfKvService, cdnPublishRepository)

    val jobQueue = TranslationJobQueue(jobQueueRepository)
    // PipelineEventBus created first so it can be injected into UserActivityService
    // for SSE-driven onboarding step advancement.
    val pipelineEventBus = com.transloom.services.PipelineEventBus(redisUrl)

    val notificationService = NotificationService.fromEnv(
        host = getSecretValue("smtp-host").ifBlank { "smtp.gmail.com" },
        port = getSecretValue("smtp-port").toIntOrNull() ?: 587,
        user = getSecretValue("smtp-user"),
        password = getSecretValue("smtp-password"),
        fromName = "Transloom"
    )
    if (notificationService.isConfigured) {
        log.info("Email notifications enabled via SMTP")
    } else {
        log.info("Email notifications disabled — set smtp-user + smtp-password secrets to enable")
    }

    val inAppNotificationService = InAppNotificationService(notificationRepository, pipelineEventBus)

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
    val razorpayService = RazorpayBillingService(billingRepository, userActivityService)
    val lifecycleMonitor = UserLifecycleMonitor(userActivityService, notificationService, inAppNotificationService)
    lifecycleMonitor.start()
    val webhookDispatcher = RazorpayWebhookDispatcher(
        webhookSecret = getSecretValue("razorpay-webhook-secret"),
        handlers = listOf(razorpayService)
    )
    val githubService = GitHubService()
    val translationService = TranslationService(memoryRepository, sharedMemoryRepository)
    val semanticChangeAnalyzer: SemanticChangeAnalyzer by inject()
    val culturalSensitivityAnalyzer: CulturalSensitivityAnalyzer by inject()
    val pipeline = TranslationPipeline(githubService, translationService, billingService, projectRepository, translationRepository, pipelineEventBus, semanticChangeAnalyzer, culturalSensitivityAnalyzer, cdnPublishService, sharedMemoryRepository)

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
            launch {
                runCatching {
                    inAppNotificationService.notifyGitHubTokenInvalid(project.ownerId, project.githubRepo)
                }
            }
            return@startWorker
        }
        pipeline.processWebhookPayload(payload, project, config, githubToken)

        // Create in-app notification + optional email when pipeline produces a PR.
        val completedRun = runCatching { pipelineEventBus.recentRuns(project.ownerId) }
            .getOrElse { emptyList() }
            .firstOrNull { it.projectId == project.id && it.prUrl != null }
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
                        commitShort = completedRun.commitShort
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

    launch {
        delay(10_000)
        val staleBefore = Clock.System.now() - WEBHOOK_HEAL_STALENESS
        val staleProjects = runCatching { projectRepository.listProjectsNeedingWebhookHeal(staleBefore) }.getOrElse {
            log.error("Webhook self-heal: failed to query stale projects — {}", it.message); emptyList()
        }
        if (staleProjects.isEmpty()) {
            log.info("Webhook self-heal: all webhooks verified within the last {} days", WEBHOOK_HEAL_STALENESS.inWholeDays)
            return@launch
        }
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
            "cloudflare kv service"  to { cfKvService.close() }
        ).forEach { (name, closer) ->
            runCatching(closer).onFailure { log.error("Failed to close {}: {}", name, it.message) }
        }
        log.info("All resources closed on application stop")
    }

    installTransloomRoutes(TransloomDeps(
        jwtSecret = jwtSecret,
        jobQueue = jobQueue,
        webhookDispatcher = webhookDispatcher,
        projectRepository = projectRepository,
        userRepository = userRepository,
        billingRepository = billingRepository,
        translationRepository = translationRepository,
        glossaryRepository = glossaryRepository,
        notificationRepository = notificationRepository,
        cdnPublishRepository = cdnPublishRepository,
        billingService = billingService,
        razorpayService = razorpayService,
        githubService = githubService,
        userActivityService = userActivityService,
        pipelineEventBus = pipelineEventBus,
        cdnPublishService = cdnPublishService,
        cfKvService = cfKvService,
    ))
}
