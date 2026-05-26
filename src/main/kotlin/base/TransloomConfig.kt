package bose.ankush.base

import com.androidplay.core.mongo.MongoConnection
import com.androidplay.core.mongo.MongoIndexer
import com.androidplay.core.queue.JobQueueRepository
import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.androidplay.core.secrets.getSecretValue
import com.transloom.TransloomDeps
import com.transloom.di.transloomIndexes
import com.transloom.installTransloomRoutes
import com.transloom.pipeline.TranslationPipeline
import com.transloom.pipeline.buildConfigWithGlossary
import com.transloom.queue.TranslationJobQueue
import com.transloom.repository.CdnPublishRepository
import com.transloom.repository.GlossaryRepository
import com.transloom.repository.mongo.*
import com.transloom.services.*
import domain.service.RefundService
import io.ktor.server.application.*
import kotlinx.coroutines.launch
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
    val pipelineRunRepository = MongoPipelineRunRepository(db)
    val memberUsageRepository = MongoMemberUsageRepository(db)
    val memberUsageService = MemberUsageService(memberUsageRepository)
    val pipelineEventBus = PipelineEventBus(redisUrl = null, runRepository = pipelineRunRepository)
    val semanticChangeAnalyzer = SemanticChangeAnalyzer(MongoSemanticChangeCacheRepository(db))
    val culturalSensitivityAnalyzer = CulturalSensitivityAnalyzer(MongoCulturalAnalysisCacheRepository(db))
    val cdnPublishRepository: CdnPublishRepository = MongoCdnPublishRepository(db)
    val notificationRepository = MongoNotificationRepository(db)
    val membershipRepository = MongoProjectMembershipRepository(db)
    val analyticsService = AnalyticsService(
        pipelineRunRepository = pipelineRunRepository,
        memberUsageRepository = memberUsageRepository,
        billingRepository = billingRepository,
        translationRepository = translationRepository,
        projectRepository = projectRepository,
        membershipRepository = membershipRepository,
        userRepository = userRepository
    )
    // Idempotent OWNER backfill for legacy projects — runs in background, doesn't gate startup.
    launch {
        runCatching { backfillProjectMemberships(projectRepository, userRepository, membershipRepository) }
            .onFailure { log.warn("Membership backfill failed: {}", it.message) }
    }
    val cfKvService = CloudflareKvService(
        accountId = getSecretValue("cloudflare-account-id"),
        namespaceId = getSecretValue("cloudflare-kv-namespace-id"),
        apiToken = getSecretValue("cloudflare-api-token")
    )
    val cdnPublishService = CdnPublishService(translationRepository, cfKvService, cdnPublishRepository)
    val pipeline = TranslationPipeline(
        githubService, translationService, billingService, projectRepository, translationRepository,
        pipelineEventBus, semanticChangeAnalyzer, culturalSensitivityAnalyzer, cdnPublishService,
        sharedMemoryRepository = null, memberUsageService = memberUsageService
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
        semanticChangeAnalyzer.close()
        culturalSensitivityAnalyzer.close()
        razorpayService.close()
        lifecycleMonitor.stop()
        log.info("Transloom resources closed")
    }

    installTransloomRoutes(
        TransloomDeps(
            jwtSecret = jwtSecret,
            jobQueue = jobQueue,
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
            cfKvService = cfKvService,
            translationService = translationService,
            analyticsService = analyticsService,
        )
    )
}
