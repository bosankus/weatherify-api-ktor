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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.netty.EngineMain
import com.androidplay.core.di.coreInfraModule
import com.transloom.di.transloomModule
import com.transloom.di.transloomIndexes
import com.androidplay.core.mongo.MongoIndexer
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.transloom.repository.ProjectRepository
import com.transloom.repository.UserRepository
import com.transloom.repository.TranslationRepository
import com.transloom.repository.BillingRepository
import com.transloom.repository.TranslationMemoryRepository
import com.transloom.pipeline.TranslationPipeline
import com.transloom.pipeline.buildConfigWithGlossary
import com.transloom.queue.TranslationJobQueue
import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.transloom.routes.*
import com.transloom.services.BillingService
import com.transloom.services.GitHubService
import com.transloom.services.RazorpayBillingService
import com.transloom.services.TranslationService
import com.androidplay.core.secrets.getSecretValue
import com.androidplay.core.cache.CacheRepository
import com.androidplay.core.queue.JobQueueRepository
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory

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
    val translationRepository: TranslationRepository by inject()
    val billingRepository: BillingRepository by inject()
    val memoryRepository: TranslationMemoryRepository by inject()
    val jobQueueRepository: JobQueueRepository by inject()
    val cacheRepository: CacheRepository by inject()

    val jobQueue = TranslationJobQueue(jobQueueRepository)
    val billingService = BillingService(billingRepository)
    val razorpayService = RazorpayBillingService(billingRepository)
    val webhookDispatcher = RazorpayWebhookDispatcher(
        webhookSecret = getSecretValue("razorpay-webhook-secret"),
        handlers = listOf(razorpayService)
    )
    val githubService = GitHubService()
    val translationService = TranslationService(memoryRepository)
    val pipelineEventBus = com.transloom.services.PipelineEventBus()
    val pipeline = TranslationPipeline(githubService, translationService, billingService, projectRepository, translationRepository, pipelineEventBus)

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
        log.info("Webhook self-heal: checking all projects...")
        val allProjects = runCatching { projectRepository.listAll() }.getOrElse {
            log.error("Webhook self-heal: failed to list projects — {}", it.message); emptyList()
        }
        var healed = 0
        for (project in allProjects) {
            val owner = userRepository.findById(project.ownerId)
            val token = owner?.githubToken
            if (token == null) continue
            runCatching { githubService.ensureWebhook(project.githubRepo, token) }
                .onSuccess { if (it) healed++ }
                .onFailure { log.warn("Webhook self-heal failed for {}: {}", project.githubRepo, it.message) }
            delay(500)
        }
        log.info("Webhook self-heal complete: {}/{} webhooks updated", healed, allProjects.size)
    }

    environment.monitor.subscribe(ApplicationStopped) {
        jobQueue.close()
        cacheRepository.close()
        githubService.close()
        translationService.close()
        razorpayService.close()
        log.info("All resources closed on application stop")
    }

    routing {
        configurePortalRoutes(jwtSecret)
        configureWebhookRoutes(jobQueue, projectRepository)
        configureAuthRoutes(jwtSecret, userRepository)
        configureRazorpayWebhook(webhookDispatcher)
        configurePublicCheckoutRoute(razorpayService, userRepository, jwtSecret)
        configureBillingReceiptRoute(jwtSecret, billingRepository, userRepository)
        authenticate("auth-jwt") {
            configureApiRoutes(billingService, githubService, projectRepository, userRepository, translationRepository, pipelineEventBus, jwtSecret, jobQueue)
            configureDashboardRoutes(projectRepository, translationRepository, billingRepository)
            configureBillingRoutes(razorpayService, billingRepository, userRepository)
        }
    }
}
