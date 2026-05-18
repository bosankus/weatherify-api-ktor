package bose.ankush.base

import com.androidplay.core.mongo.MongoConnection
import com.androidplay.core.mongo.MongoIndexer
import com.androidplay.core.queue.JobQueueRepository
import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.androidplay.core.secrets.getSecretValue
import com.transloom.di.transloomIndexes
import com.transloom.pipeline.TranslationPipeline
import com.transloom.pipeline.buildConfigWithGlossary
import com.transloom.queue.TranslationJobQueue
import com.transloom.repository.GlossaryRepository
import com.transloom.repository.mongo.*
import com.transloom.routes.*
import com.transloom.services.BillingService
import com.transloom.services.GitHubService
import com.transloom.services.PipelineEventBus
import com.transloom.services.RazorpayBillingService
import com.transloom.services.TranslationService
import com.transloom.services.UserActivityService
import com.transloom.services.UserLifecycleMonitor
import domain.service.RefundService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

fun Application.configureTransloom(refundService: RefundService) {
    val log = LoggerFactory.getLogger("Transloom")

    val jwtSecret = getSecretValue("jwt-secret")
    val mongoUri = getSecretValue("db-connection-string")
    val encryptionKey = getSecretValue("token-encryption-key")
    val webhookSecret = getSecretValue("razorpay-webhook-secret")

    val db = MongoConnection.connect(mongoUri, "transloom")
    runBlocking { MongoIndexer.ensure(db, transloomIndexes()) }

    val userRepository = MongoUserRepository(db, encryptionKey)
    val projectRepository = MongoProjectRepository(db)
    val glossaryRepository: GlossaryRepository = MongoGlossaryRepository(db)
    val translationRepository = MongoTranslationRepository(db)
    val billingRepository = MongoBillingRepository(db, projectRepository)
    val memoryRepository = MongoTranslationMemoryRepository(db)
    val userActivityRepository = MongoUserActivityRepository(db)

    val jobQueueRepository: JobQueueRepository by inject()

    val jobQueue = TranslationJobQueue(jobQueueRepository)
    val billingService = BillingService(billingRepository)
    val userActivityService = UserActivityService(
        userRepository = userRepository,
        userActivityRepository = userActivityRepository,
        billingRepository = billingRepository,
        projectRepository = projectRepository
    )
    val razorpayService = RazorpayBillingService(billingRepository, userActivityService)
    val lifecycleMonitor = UserLifecycleMonitor(userActivityService).also { it.start() }
    val githubService = GitHubService()
    val translationService = TranslationService(memoryRepository)
    val pipelineEventBus = PipelineEventBus()
    val pipeline = TranslationPipeline(
        githubService, translationService, billingService, projectRepository, translationRepository, pipelineEventBus
    )

    // Central webhook dispatcher — register all Razorpay event handlers here.
    // Adding a new family of events (e.g. payment.*) = implement RazorpayEventHandler + add to this list.
    val webhookDispatcher = RazorpayWebhookDispatcher(
        webhookSecret = webhookSecret,
        handlers = listOf(
            razorpayService,                          // subscription.*
            refundService as com.androidplay.core.razorpay.RazorpayEventHandler  // refund.*
        )
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
            log.warn("Project owner has no GitHub token — cannot process webhook")
            return@startWorker
        }
        pipeline.processWebhookPayload(payload, project, config, githubToken)
    }

    monitor.subscribe(ApplicationStopped) {
        jobQueue.close()
        githubService.close()
        translationService.close()
        razorpayService.close()
        lifecycleMonitor.stop()
        log.info("Transloom resources closed")
    }

    routing {
        configurePortalRoutes(jwtSecret)
        configureWebhookRoutes(jobQueue, projectRepository)
        configureAuthRoutes(jwtSecret, userRepository, userActivityService)
        configureRazorpayWebhook(webhookDispatcher)
        configurePublicCheckoutRoute(razorpayService, userRepository, billingRepository, jwtSecret, userActivityService)
        configureBillingReceiptRoute(jwtSecret, billingRepository, userRepository, userActivityService)
        authenticate("auth-jwt") {
            configureApiRoutes(billingService, githubService, projectRepository, userRepository, translationRepository, pipelineEventBus, jwtSecret, jobQueue, glossaryRepository, userActivityService)
            configureDashboardRoutes(projectRepository, translationRepository, billingRepository)
            configureBillingRoutes(razorpayService, billingRepository, userRepository, jwtSecret, userActivityService)
            configureInsightsRoutes(userActivityService)
            configureOnboardingRoutes(userRepository, billingRepository, projectRepository)
        }
    }
}
