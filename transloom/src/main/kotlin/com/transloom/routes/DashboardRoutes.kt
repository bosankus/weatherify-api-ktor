package com.transloom.routes

import com.transloom.repository.BillingRepository
import com.transloom.repository.ProjectRepository
import com.transloom.repository.TranslationRepository
import com.transloom.model.ApiError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
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

fun Route.configureDashboardRoutes(
    projectRepository: ProjectRepository,
    translationRepository: TranslationRepository,
    billingRepository: BillingRepository,
    cdnPublishRepository: com.transloom.repository.CdnPublishRepository
) {
    route("/transloom/api/dashboard") {

        get("/stats") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            data class StatsResult(
                val statusCounts: Map<String, Int>,
                val plan: com.transloom.domain.BillingPlan,
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
            val items = projectsList.flatMap { project ->
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

        get("/cdn-status") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))

            val projects = projectRepository.listForUser(userId)

            @kotlinx.serialization.Serializable
            data class CdnPublishInfo(
                val projectId: String,
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
