package com.syncling

import com.androidplay.core.razorpay.RazorpayWebhookDispatcher
import com.syncling.queue.TranslationJobQueue
import com.syncling.repository.BillingRepository
import com.syncling.repository.CdnPublishRepository
import com.syncling.repository.GlossaryRepository
import com.syncling.repository.NotificationRepository
import com.syncling.repository.ProjectMembershipRepository
import com.syncling.repository.ProjectRepository
import com.syncling.repository.TranslationRepository
import com.syncling.repository.UserRepository
import com.syncling.routes.configureAnalyticsRoutes
import com.syncling.routes.configureApiRoutes
import com.syncling.routes.configureAuthRoutes
import com.syncling.routes.configureBillingReceiptRoute
import com.syncling.routes.configureBillingRoutes
import com.syncling.routes.configureCdnBundleRoutes
import com.syncling.routes.configureCdnPublishRoute
import com.syncling.routes.configureDashboardRoutes
import com.syncling.routes.configureInsightsRoutes
import com.syncling.routes.configureNotificationRoutes
import com.syncling.routes.configureOnboardingRoutes
import com.syncling.routes.configurePortalRoutes
import com.syncling.routes.configurePublicCheckoutRoute
import com.syncling.routes.configureRazorpayWebhook
import com.syncling.routes.configureWebhookRoutes
import com.syncling.routes.configureSupportRoutes
import io.ktor.server.http.content.staticResources
import com.syncling.services.AnalyticsService
import com.syncling.services.BillingService
import com.syncling.services.CdnPublishService
import com.syncling.services.GitHubService
import com.syncling.services.InAppNotificationService
import com.syncling.services.NotificationService
import com.syncling.services.PipelineEventBus
import com.syncling.services.RazorpayBillingService
import com.syncling.services.TranslationService
import com.syncling.services.UserActivityService
import com.syncling.routes.configureMemberRoutes
import com.syncling.routes.configureCdnSigningKeyRoute
import com.syncling.routes.configureTokenApiRoutes
import com.syncling.routes.configureTokenPortalRoute
import com.syncling.repository.ApiTokenRepository
import com.syncling.repository.PipelineRunRepository
import com.syncling.repository.SupportTicketRepository
import com.syncling.routes.ApiTokenRepoAttr
import com.androidplay.core.queue.JobQueueRepository
import io.ktor.util.AttributeKey
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import com.syncling.routes.landingPage
import io.ktor.server.html.respondHtml
import io.ktor.server.request.host
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.bson.Document

/** Application-level attribute so admin routes (in the main src module) can access the repo. */
val SupportTicketRepoKey = AttributeKey<SupportTicketRepository>("SupportTicketRepository")

@Serializable
data class HealthStatus(val status: String)

@Serializable
data class ReadinessStatus(val db: String, val redis: String)

class SynclingDeps(
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
    val supportTicketRepository: SupportTicketRepository? = null,
    val supportAdminEmail: String = "support@androidplay.in",
    val apiTokenRepository: ApiTokenRepository? = null,
)

/**
 * Single canonical wiring of every Syncling route. Called from both the standalone
 * syncling module entrypoint and the root weatherify-api app so the two cannot drift.
 * Required RateLimitName registrations: auth, github_webhook, razorpay_webhook,
 * bundle_fetch, manual_sync (the last one is used inside configureApiRoutes).
 */
fun Application.installSynclingRoutes(d: SynclingDeps) {
    d.supportTicketRepository?.let { attributes.put(SupportTicketRepoKey, it) }
    d.apiTokenRepository?.let { attributes.put(ApiTokenRepoAttr, it) }
    routing {
        get("/") {
            val host = call.request.host()
            if (host == "syncling.space" || host == "www.syncling.space") {
                call.respondHtml { landingPage() }
            } else {
                call.respondRedirect("/login", permanent = false)
            }
        }

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

        get("/robots.txt") {
            call.respondText("""
User-agent: *
Allow: /syncling
Allow: /auth/github
Allow: /docs
Disallow: /api/
Disallow: /app
Disallow: /billing
Disallow: /projects
Disallow: /review-portal
Disallow: /members
Disallow: /invite/
Disallow: /health
Disallow: /ready
Disallow: /admin

Sitemap: https://syncling.space/sitemap.xml
""".trimIndent(), contentType = io.ktor.http.ContentType.Text.Plain)
        }

        get("/sitemap.xml") {
            call.respondText("""<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>https://syncling.space/syncling</loc>
    <changefreq>weekly</changefreq>
    <priority>1.0</priority>
  </url>
  <url>
    <loc>https://syncling.space/syncling#features</loc>
    <changefreq>monthly</changefreq>
    <priority>0.8</priority>
  </url>
  <url>
    <loc>https://syncling.space/syncling#pricing</loc>
    <changefreq>monthly</changefreq>
    <priority>0.8</priority>
  </url>
  <url>
    <loc>https://syncling.space/syncling#faq</loc>
    <changefreq>monthly</changefreq>
    <priority>0.7</priority>
  </url>
  <url>
    <loc>https://syncling.space/syncling#how</loc>
    <changefreq>monthly</changefreq>
    <priority>0.7</priority>
  </url>
  <url>
    <loc>https://syncling.space/syncling#cli</loc>
    <changefreq>monthly</changefreq>
    <priority>0.7</priority>
  </url>
  <url>
    <loc>https://syncling.space/syncling/docs</loc>
    <changefreq>weekly</changefreq>
    <priority>0.9</priority>
  </url>
</urlset>""", contentType = io.ktor.http.ContentType.Application.Xml)
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
        d.apiTokenRepository?.let { configureTokenPortalRoute() }
        authenticate("auth-jwt", "api-token") {
            d.apiTokenRepository?.let { configureTokenApiRoutes(it) }
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
            d.supportTicketRepository?.let { repo ->
                configureSupportRoutes(repo, d.userRepository, d.notificationService, d.supportAdminEmail)
            }
        }
    }
}
