package com.transloom

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.netty.EngineMain
import kotlin.time.Duration.Companion.seconds
import com.androidplay.core.di.coreInfraModule
import com.transloom.di.transloomModule
import com.transloom.di.transloomIndexes
import com.androidplay.core.mongo.MongoIndexer
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import com.transloom.repository.ProjectRepository
import com.transloom.repository.UserRepository
import com.transloom.repository.TranslationRepository
import com.transloom.repository.BillingRepository
import com.transloom.repository.TranslationMemoryRepository
import com.transloom.repository.UserActivityRepository
import com.transloom.pipeline.TranslationPipeline
import com.transloom.pipeline.buildConfigWithGlossary
import com.transloom.repository.GlossaryRepository
import com.transloom.queue.TranslationJobQueue
import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.transloom.routes.*
import com.transloom.services.BillingService
import com.transloom.services.CulturalSensitivityAnalyzer
import com.transloom.services.GitHubService
import com.transloom.services.RazorpayBillingService
import com.transloom.services.SemanticChangeAnalyzer
import com.transloom.services.TranslationService
import com.transloom.services.UserActivityService
import com.transloom.services.UserLifecycleMonitor
import com.transloom.services.CloudflareKvService
import com.transloom.services.CdnPublishService
import com.transloom.repository.CdnPublishRepository
import com.androidplay.core.secrets.getSecretValue
import com.androidplay.core.cache.CacheRepository
import com.androidplay.core.queue.JobQueueRepository
import io.ktor.server.plugins.origin
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory

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
    }

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
    val billingRepository: BillingRepository by inject()
    val memoryRepository: TranslationMemoryRepository by inject()
    val userActivityRepository: UserActivityRepository by inject()
    val jobQueueRepository: JobQueueRepository by inject()
    val cacheRepository: CacheRepository by inject()
    val cdnPublishRepository: CdnPublishRepository by inject()

    val cfAccountId = getSecretValue("cloudflare-account-id")
    val cfNamespaceId = getSecretValue("cloudflare-kv-namespace-id")
    val cfApiToken = getSecretValue("cloudflare-api-token")
    val cfKvService = CloudflareKvService(cfAccountId, cfNamespaceId, cfApiToken)
    val cdnPublishService = CdnPublishService(translationRepository, cfKvService, cdnPublishRepository)

    val jobQueue = TranslationJobQueue(jobQueueRepository)
    // PipelineEventBus created first so it can be injected into UserActivityService
    // for SSE-driven onboarding step advancement.
    val pipelineEventBus = com.transloom.services.PipelineEventBus(redisUrl)
    val userActivityService = UserActivityService(
        userRepository = userRepository,
        userActivityRepository = userActivityRepository,
        billingRepository = billingRepository,
        projectRepository = projectRepository,
        eventBus = pipelineEventBus
    )
    val billingService = BillingService(billingRepository, userActivityService)
    val razorpayService = RazorpayBillingService(billingRepository, userActivityService)
    val lifecycleMonitor = UserLifecycleMonitor(userActivityService)
    lifecycleMonitor.start()
    val webhookDispatcher = RazorpayWebhookDispatcher(
        webhookSecret = getSecretValue("razorpay-webhook-secret"),
        handlers = listOf(razorpayService)
    )
    val githubService = GitHubService()
    val translationService = TranslationService(memoryRepository)
    val semanticChangeAnalyzer: SemanticChangeAnalyzer by inject()
    val culturalSensitivityAnalyzer: CulturalSensitivityAnalyzer by inject()
    val pipeline = TranslationPipeline(githubService, translationService, billingService, projectRepository, translationRepository, pipelineEventBus, semanticChangeAnalyzer, culturalSensitivityAnalyzer, cdnPublishService)

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
            log.warn("Project owner has no GitHub token linked — cannot process webhook")
            return@startWorker
        }
        pipeline.processWebhookPayload(payload, project, config, githubToken)
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
        jobQueue.close()
        pipelineEventBus.close()
        cacheRepository.close()
        githubService.close()
        translationService.close()
        semanticChangeAnalyzer.close()
        culturalSensitivityAnalyzer.close()
        razorpayService.close()
        lifecycleMonitor.stop()
        cfKvService.close()
        log.info("All resources closed on application stop")
    }

    routing {
        configurePortalRoutes(jwtSecret)
        rateLimit(RateLimitName("github_webhook")) {
            configureWebhookRoutes(jobQueue, projectRepository, billingRepository)
        }
        rateLimit(RateLimitName("auth")) {
            configureAuthRoutes(jwtSecret, userRepository, userActivityService)
        }
        rateLimit(RateLimitName("razorpay_webhook")) {
            configureRazorpayWebhook(webhookDispatcher)
        }
        configurePublicCheckoutRoute(razorpayService, userRepository, billingRepository, jwtSecret, userActivityService)
        configureBillingReceiptRoute(jwtSecret, billingRepository, userRepository, userActivityService)
        authenticate("auth-jwt") {
            configureApiRoutes(billingService, billingRepository, githubService, projectRepository, userRepository, translationRepository, pipelineEventBus, jobQueue, glossaryRepository, userActivityService, cdnPublishService)
            configureDashboardRoutes(projectRepository, translationRepository, billingRepository, cdnPublishRepository)
            configureBillingRoutes(razorpayService, billingRepository, userRepository, jwtSecret, userActivityService)
            configureInsightsRoutes(userActivityService)
            configureOnboardingRoutes(userRepository, billingRepository, projectRepository)
            configureCdnPublishRoute(projectRepository, cdnPublishService)
        }
    }
}
