package com.transloom

import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.transloom.queue.TranslationJobQueue
import com.transloom.repository.BillingRepository
import com.transloom.repository.CdnPublishRepository
import com.transloom.repository.GlossaryRepository
import com.transloom.repository.NotificationRepository
import com.transloom.repository.ProjectMembershipRepository
import com.transloom.repository.ProjectRepository
import com.transloom.repository.TranslationRepository
import com.transloom.repository.UserRepository
import com.transloom.routes.configureAnalyticsRoutes
import com.transloom.routes.configureApiRoutes
import com.transloom.routes.configureAuthRoutes
import com.transloom.routes.configureBillingReceiptRoute
import com.transloom.routes.configureBillingRoutes
import com.transloom.routes.configureCdnBundleRoutes
import com.transloom.routes.configureCdnPublishRoute
import com.transloom.routes.configureDashboardRoutes
import com.transloom.routes.configureInsightsRoutes
import com.transloom.routes.configureNotificationRoutes
import com.transloom.routes.configureOnboardingRoutes
import com.transloom.routes.configurePortalRoutes
import com.transloom.routes.configurePublicCheckoutRoute
import com.transloom.routes.configureRazorpayWebhook
import com.transloom.routes.configureWebhookRoutes
import io.ktor.server.http.content.staticResources
import com.transloom.services.AnalyticsService
import com.transloom.services.BillingService
import com.transloom.services.CdnPublishService
import com.transloom.services.GitHubService
import com.transloom.services.InAppNotificationService
import com.transloom.services.NotificationService
import com.transloom.services.PipelineEventBus
import com.transloom.services.RazorpayBillingService
import com.transloom.services.TranslationService
import com.transloom.services.UserActivityService
import com.transloom.routes.configureMemberRoutes
import com.transloom.routes.configureCdnSigningKeyRoute
import com.transloom.repository.PipelineRunRepository
import com.androidplay.core.queue.JobQueueRepository
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.bson.Document

@Serializable
data class HealthStatus(val status: String)

@Serializable
data class ReadinessStatus(val db: String, val redis: String)

class TransloomDeps(
    val jwtSecret: String,
    val jobQueue: TranslationJobQueue,
    val db: MongoDatabase,
    val jobQueueRepository: JobQueueRepository,
    val webhookDispatcher: RazorpayWebhookDispatcher,
    val projectRepository: ProjectRepository,
    val userRepository: UserRepository,
    val billingRepository: BillingRepository,
    val translationRepository: TranslationRepository,
    val glossaryRepository: GlossaryRepository,
    val notificationRepository: NotificationRepository,
    val membershipRepository: ProjectMembershipRepository,
    val cdnPublishRepository: CdnPublishRepository,
    val billingService: BillingService,
    val razorpayService: RazorpayBillingService,
    val githubService: GitHubService,
    val userActivityService: UserActivityService,
    val pipelineEventBus: PipelineEventBus,
    val cdnPublishService: CdnPublishService,
    val translationService: TranslationService,
    val analyticsService: AnalyticsService,
    val notificationService: NotificationService? = null,
    val inAppNotificationService: InAppNotificationService? = null,
    val pipelineRunRepository: PipelineRunRepository? = null,
)

/**
 * Single canonical wiring of every Transloom route. Called from both the standalone
 * transloom module entrypoint and the root weatherify-api app so the two cannot drift.
 * Required RateLimitName registrations: auth, github_webhook, razorpay_webhook,
 * bundle_fetch, manual_sync (the last one is used inside configureApiRoutes).
 */
fun Application.installTransloomRoutes(d: TransloomDeps) {
    routing {
        // Liveness probe — always 200 if the JVM is running.
        get("/health") { call.respond(HealthStatus("ok")) }

        // Readiness probe — checks DB and Redis reachability.
        // Returns 503 if any dependency is unhealthy so the load balancer stops routing traffic.
        get("/ready") {
            val dbStatus = try {
                withTimeoutOrNull(2_000) { d.db.runCommand(Document("ping", 1)); "ok" } ?: "timeout"
            } catch (e: Exception) { "error" }

            val redisStatus = if (d.jobQueueRepository.isConnected()) "ok" else "unavailable"

            val overallOk = dbStatus == "ok"
            call.respond(
                if (overallOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                ReadinessStatus(db = dbStatus, redis = redisStatus)
            )
        }

        // Serve portal CSS/JS bundles from src/main/resources/static at /transloom/static/*.
        // Cached aggressively in prod via the etag plugin (default Ktor behavior).
        staticResources("/transloom/static", "static")
        configurePortalRoutes(d.jwtSecret)
        rateLimit(RateLimitName("github_webhook")) {
            configureWebhookRoutes(d.jobQueue, d.projectRepository, d.billingRepository)
        }
        rateLimit(RateLimitName("auth")) {
            configureAuthRoutes(d.jwtSecret, d.userRepository, d.userActivityService)
        }
        rateLimit(RateLimitName("razorpay_webhook")) {
            configureRazorpayWebhook(d.webhookDispatcher)
        }
        configurePublicCheckoutRoute(d.razorpayService, d.userRepository, d.billingRepository, d.jwtSecret, d.userActivityService)
        configureBillingReceiptRoute(d.jwtSecret, d.billingRepository, d.userRepository, d.userActivityService)
        rateLimit(RateLimitName("bundle_fetch")) {
            configureCdnBundleRoutes(d.projectRepository, d.cdnPublishRepository, d.cdnPublishService, d.membershipRepository)
        }
        authenticate("auth-jwt") {
            configureApiRoutes(
                d.billingService, d.billingRepository, d.githubService, d.projectRepository,
                d.userRepository, d.translationRepository, d.pipelineEventBus, d.jobQueue,
                d.glossaryRepository, d.userActivityService, d.membershipRepository,
                d.cdnPublishService, d.translationService, d.pipelineRunRepository
            )
            configureDashboardRoutes(d.projectRepository, d.translationRepository, d.billingRepository, d.cdnPublishRepository)
            configureBillingRoutes(d.razorpayService, d.billingRepository, d.userRepository, d.jwtSecret, d.userActivityService)
            configureAnalyticsRoutes(d.analyticsService, d.billingRepository)
            configureInsightsRoutes(d.userActivityService)
            configureOnboardingRoutes(d.userRepository, d.billingRepository, d.projectRepository, d.translationRepository)
            configureCdnPublishRoute(d.projectRepository, d.cdnPublishService, d.membershipRepository)
            configureCdnSigningKeyRoute(d.projectRepository, d.cdnPublishService, d.membershipRepository)
            configureNotificationRoutes(d.notificationRepository)
            configureMemberRoutes(
                d.membershipRepository, d.projectRepository, d.userRepository,
                d.billingService, d.notificationService, d.inAppNotificationService
            )
        }
    }
}
