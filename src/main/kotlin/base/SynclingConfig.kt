package bose.ankush.base

import com.androidplay.core.mongo.MongoConnection
import com.androidplay.core.mongo.MongoIndexer
import com.androidplay.core.queue.JobQueueRepository
import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.androidplay.core.secrets.getSecretValue
import com.syncling.SynclingDeps
import com.syncling.di.synclingIndexes
import com.syncling.installSynclingRoutes
import com.syncling.pipeline.TranslationPipeline
import com.syncling.pipeline.buildConfigWithGlossary
import com.syncling.queue.TranslationJobQueue
import com.syncling.repository.CdnPublishRepository
import com.syncling.repository.GlossaryRepository
import com.syncling.repository.mongo.*
import com.syncling.repository.mongo.MongoApiTokenRepository
import com.syncling.repository.mongo.MongoSupportTicketRepository
import com.syncling.services.*
import domain.service.RefundService
import io.ktor.server.application.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.minutes
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

fun Application.configureSyncling(refundService: RefundService) {
    val log = LoggerFactory.getLogger("Syncling")

    // Prometheus registry + Micrometer plugin. Mirrors what the syncling-only entrypoint
    // does in `com.syncling.Application`. Exposed at `/metrics` via SynclingDeps.meterRegistry.
    val meterRegistry = io.micrometer.prometheusmetrics.PrometheusMeterRegistry(
        io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT
    )
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
    val pipelineMetrics = PipelineMetrics(meterRegistry)

    val jwtSecret = getSecretValue("jwt-secret")
    // "mongo-uri" is the Syncling/transloom Atlas cluster; "db-connection-string" is the
    // separate weatherify cluster and does NOT have transloom credentials — using it here
    // caused MongoCommandException (auth failed) on every request → 500 for all API routes.
    val mongoUri = getSecretValue("mongo-uri")
    val encryptionKey = getSecretValue("token-encryption-key")
    val webhookSecret = getSecretValue("razorpay-webhook-secret")
    val redisUrl = getSecretValue("redis-url").ifBlank { null }

    val db = MongoConnection.connect(mongoUri, "transloom")
    runBlocking {
        runCatching { MongoIndexer.ensure(db, synclingIndexes()) }
            .onFailure { log.error("Syncling MongoDB index setup failed — DB may be unreachable: {}", it.message, it) }
    }

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
    val githubService = GitHubService()
    val quotaBlockedRunRepository = MongoQuotaBlockedRunRepository(db)
    val quotaResumeService = QuotaResumeService(
        billingRepository = billingRepository,
        blockedRunRepository = quotaBlockedRunRepository,
        userRepository = userRepository,
        githubService = githubService,
        jobQueue = jobQueue,
        userActivityService = userActivityService
    )
    val razorpayService = RazorpayBillingService(
        billingRepository, userActivityService,
        quotaResumeService = quotaResumeService
    )
    val lifecycleMonitor = UserLifecycleMonitor(
        userActivityService,
        quotaResumeService = quotaResumeService
    ).also { it.start() }
    val translationService = TranslationService(memoryRepository)
    val pipelineRunRepository = MongoPipelineRunRepository(db)
    val memberUsageRepository = MongoMemberUsageRepository(db)
    val memberUsageService = MemberUsageService(memberUsageRepository)
    val pipelineEventBus = PipelineEventBus(redisUrl = redisUrl, runRepository = pipelineRunRepository)
    val semanticChangeAnalyzer = SemanticChangeAnalyzer(MongoSemanticChangeCacheRepository(db))
    val culturalSensitivityAnalyzer = CulturalSensitivityAnalyzer(MongoCulturalAnalysisCacheRepository(db))
    val cdnPublishRepository: CdnPublishRepository = MongoCdnPublishRepository(db)
    val notificationRepository = MongoNotificationRepository(db)
    val membershipRepository = MongoProjectMembershipRepository(db)
    val supportTicketRepository = MongoSupportTicketRepository(db)
    val apiTokenRepository = MongoApiTokenRepository(db)
    val figmaCandidateRepository = com.syncling.repository.mongo.MongoFigmaCandidateRepository(db)
    val figmaNodeBindingRepository = com.syncling.repository.mongo.MongoFigmaNodeBindingRepository(db)
    val figmaPreviewRepository = com.syncling.repository.mongo.MongoFigmaPreviewRepository(db)
    val figmaSettingsRepository = com.syncling.repository.mongo.MongoFigmaSettingsRepository(db)
    val figmaEmbeddingService = EmbeddingService()
    val translationEmbeddingRepository = MongoTranslationEmbeddingRepository(db)
    val figmaInAppNotificationService = InAppNotificationService(notificationRepository, pipelineEventBus)
    val figmaSyncService = com.syncling.services.FigmaSyncService(
        candidateRepository = figmaCandidateRepository,
        bindingRepository = figmaNodeBindingRepository,
        translationRepository = translationRepository,
        gitHubService = githubService,
        previewRepository = figmaPreviewRepository,
        embeddingService = figmaEmbeddingService,
        embeddingRepository = translationEmbeddingRepository,
        notificationService = figmaInAppNotificationService
    )
    val analyticsService = AnalyticsService(
        pipelineRunRepository = pipelineRunRepository,
        memberUsageRepository = memberUsageRepository,
        billingRepository = billingRepository,
        translationRepository = translationRepository,
        projectRepository = projectRepository,
        membershipRepository = membershipRepository,
        userRepository = userRepository
    )
    val statusService = com.syncling.services.StatusService(
        pipelineRunRepository = pipelineRunRepository,
        cdnPublishRepository = cdnPublishRepository,
    )
    // Idempotent OWNER backfill for legacy projects — runs in background, doesn't gate startup.
    launch {
        runCatching { backfillProjectMemberships(projectRepository, userRepository, membershipRepository) }
            .onFailure { log.warn("Membership backfill failed: {}", it.message) }
    }
    val cfKvService = CloudflareR2Service(
        accountId = getSecretValue("cloudflare-account-id"),
        bucketName = getSecretValue("cloudflare-r2-bucket-name"),
        accessKeyId = getSecretValue("cloudflare-r2-access-key-id"),
        secretAccessKey = getSecretValue("cloudflare-r2-secret-access-key")
    )
    val cdnPublishService = CdnPublishService(translationRepository, cfKvService, cdnPublishRepository)
    val pipeline = TranslationPipeline(
        githubService, translationService, billingService, projectRepository, translationRepository,
        pipelineEventBus, semanticChangeAnalyzer, culturalSensitivityAnalyzer, cdnPublishService,
        sharedMemoryRepository = null, memberUsageService = memberUsageService,
        metrics = pipelineMetrics,
        blockedRunRepository = quotaBlockedRunRepository,
        visualContextProvider = figmaSyncService
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
        try {
            kotlinx.coroutines.withTimeout(10.minutes) {
                pipeline.processWebhookPayload(payload, project, config, githubToken)
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            log.error("Pipeline timed out after 10 minutes for repo={} project={}", project.githubRepo, project.id)
        }
    }

    monitor.subscribe(ApplicationStopped) {
        jobQueue.close()
        githubService.close()
        translationService.close()
        semanticChangeAnalyzer.close()
        culturalSensitivityAnalyzer.close()
        razorpayService.close()
        lifecycleMonitor.stop()
        cfKvService.close()
        figmaSyncService.close()
        figmaEmbeddingService.close()
        log.info("Syncling resources closed")
    }

    installSynclingRoutes(
        SynclingDeps(
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
            supportTicketRepository = supportTicketRepository,
            apiTokenRepository = apiTokenRepository,
            meterRegistry = meterRegistry,
            quotaBlockedRunRepository = quotaBlockedRunRepository,
            quotaResumeService = quotaResumeService,
            figmaSyncService = figmaSyncService,
            figmaCandidateRepository = figmaCandidateRepository,
            figmaPreviewRepository = figmaPreviewRepository,
            figmaSettingsRepository = figmaSettingsRepository,
        )
    )
}
