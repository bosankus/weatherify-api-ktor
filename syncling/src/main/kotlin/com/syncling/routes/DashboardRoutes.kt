package com.syncling.routes

import com.syncling.repository.BillingRepository
import com.syncling.repository.PipelineRunRepository
import com.syncling.repository.ProjectRepository
import com.syncling.repository.ReviewerFeedbackRepository
import com.syncling.repository.TranslationRepository
import com.syncling.model.ApiError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class DashboardStats(
    val totalStringsTranslated: Int,
    val pendingReview: Int,
    val blockedCount: Int,
    val activeLanguages: Int,
    val totalProjects: Int,
    val currentPlan: String,
    val currentPlanDisplay: String
)

@Serializable
data class GlossaryItem(
    val id: String,
    val source: String,
    val targetLanguage: String,
    val translation: String,
    val status: String
)

@Serializable
data class InvoiceItem(
    val id: String,
    val date: String,
    val amount: String,
    val status: String
)

/**
 * Roll-up of pipeline economics + quality over the last 30 days. Powers the
 * single "Pipeline Insights" sidebar widget on the dashboard. All values are
 * pre-formatted on the server so the widget can stay one tiny block of HTML.
 */
@Serializable
data class DashboardInsights(
    val windowDays: Int,
    val runs: Int,
    /** 0.0–1.0 share of strings served from translation memory (cache hits / total). */
    val memoryHitRate: Double,
    /** Estimated USD spent on Gemini calls in the window. */
    val geminiSpendUsd: Double,
    /** Estimated USD avoided thanks to memory hits (cache hits × avg cost-per-translated-string). */
    val costSavedUsd: Double,
    /** Average successful-run duration in seconds; null if no successful runs in window. */
    val avgRunSeconds: Double?,
    /** Reviewer corrections captured in the window — surface as a quality signal. */
    val reviewerEdits: Int
)

fun Route.configureDashboardRoutes(
    projectRepository: ProjectRepository,
    translationRepository: TranslationRepository,
    billingRepository: BillingRepository,
    cdnPublishRepository: com.syncling.repository.CdnPublishRepository,
    pipelineRunRepository: PipelineRunRepository? = null,
    reviewerFeedbackRepository: ReviewerFeedbackRepository? = null
) {
    route("/api/dashboard") {

        get("/stats") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            data class StatsResult(
                val statusCounts: Map<String, Int>,
                val plan: com.syncling.domain.BillingPlan,
                val total: Int,
                val languages: Int,
                val projects: Int
            )

            val result = coroutineScope {
                val a = async { translationRepository.countByStatus(userId) }
                val b = async { billingRepository.getSubscription(userId).plan }
                val c = async { translationRepository.totalStringsTranslated(userId) }
                val d = async { translationRepository.activeLanguageCount(userId) }
                val e = async { projectRepository.countForUser(userId) }
                StatsResult(a.await(), b.await(), c.await(), d.await(), e.await())
            }

            call.respond(
                DashboardStats(
                    totalStringsTranslated = result.total,
                    pendingReview = result.statusCounts["review"] ?: 0,
                    blockedCount = result.statusCounts["blocked"] ?: 0,
                    activeLanguages = result.languages,
                    totalProjects = result.projects,
                    currentPlan = result.plan.name,
                    currentPlanDisplay = result.plan.displayName
                )
            )
        }

        get("/glossary") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            val projectsList = projectRepository.listForUser(userId)
            val items = coroutineScope {
                projectsList.map { project ->
                    async {
                        val glossary = projectRepository.getGlossary(project.id)
                        glossary.flatMap { (lang, terms) ->
                            terms.entries.map { (source, target) ->
                                GlossaryItem(
                                    id = "${project.id}-$lang-$source",
                                    source = source,
                                    targetLanguage = lang,
                                    translation = target,
                                    status = "Active"
                                )
                            }
                        }
                    }
                }.awaitAll().flatten()
            }
            call.respond(items)
        }

        get("/invoices") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            val invoices = billingRepository.listInvoices(userId).map { inv ->
                val dateStr = inv.createdAt.toLocalDateTime(TimeZone.UTC).date.toString()
                InvoiceItem(
                    id = inv.razorpayPaymentId,
                    date = dateStr,
                    amount = "₹${"%.2f".format(inv.amountPaise / 100.0)}",
                    status = inv.status.replaceFirstChar { it.uppercase() }
                )
            }
            call.respond(invoices)
        }

        get("/insights") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val runRepo = pipelineRunRepository
            val feedbackRepo = reviewerFeedbackRepository
            if (runRepo == null || feedbackRepo == null) {
                return@get call.respond(
                    DashboardInsights(30, 0, 0.0, 0.0, 0.0, null, 0)
                )
            }

            val windowDays = 30
            val sinceMillis = System.currentTimeMillis() - windowDays * 24L * 60 * 60 * 1000

            val (runs, projectIds) = coroutineScope {
                val r = async { runRepo.listForOwner(userId, sinceMillis, limit = 1000) }
                val p = async { projectRepository.listForUser(userId).map { it.id } }
                r.await() to p.await()
            }

            var totalCacheHits = 0L
            var totalStrings = 0L
            var totalCostUsd = 0.0
            var successDurations = 0L
            var successRuns = 0
            for (run in runs) {
                totalCacheHits += run.cacheHits.toLong()
                totalStrings += run.stringsTranslated.toLong()
                totalCostUsd += run.estimatedCostUsd
                if (run.status == "succeeded" && (run.durationMs ?: 0L) > 0) {
                    successDurations += run.durationMs ?: 0L
                    successRuns++
                }
            }
            val combined = totalCacheHits + totalStrings
            val memoryHitRate = if (combined > 0) totalCacheHits.toDouble() / combined else 0.0
            val costPerString = if (totalStrings > 0) totalCostUsd / totalStrings else 0.0
            val costSavedUsd = costPerString * totalCacheHits
            val avgRunSeconds = if (successRuns > 0) successDurations / 1000.0 / successRuns else null

            val reviewerEdits = feedbackRepo.countForProjectsSince(projectIds, sinceMillis)

            call.respond(
                DashboardInsights(
                    windowDays = windowDays,
                    runs = runs.size,
                    memoryHitRate = memoryHitRate,
                    geminiSpendUsd = totalCostUsd,
                    costSavedUsd = costSavedUsd,
                    avgRunSeconds = avgRunSeconds,
                    reviewerEdits = reviewerEdits
                )
            )
        }

        get("/cdn-status") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            val projects = projectRepository.listForUser(userId)

            @kotlinx.serialization.Serializable
            data class CdnPublishInfo(
                val projectId: String,
                val projectName: String,
                val bundleVersion: String,
                val publishedAt: Long,
                val locales: List<String>,
                val status: String
            )

            @kotlinx.serialization.Serializable
            data class CdnStatusResponse(val publishes: List<CdnPublishInfo>)

            val publishes = projects.mapNotNull { proj ->
                runCatching { cdnPublishRepository.lastPublish(proj.id) }.getOrNull()
                    ?.let { log ->
                        CdnPublishInfo(
                            projectId = proj.id,
                            projectName = proj.name,
                            bundleVersion = log.bundleVersion,
                            publishedAt = log.publishedAt,
                            locales = log.locales,
                            status = log.status
                        )
                    }
            }
            call.respond(CdnStatusResponse(publishes))
        }
    }
}
