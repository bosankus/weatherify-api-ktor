package com.syncling.routes

import com.syncling.domain.BillingPlan
import com.syncling.model.ApiError
import com.syncling.repository.BillingRepository
import com.syncling.services.AnalyticsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Analytics endpoints, all under /syncling/api/analytics.
 *
 * Gating:
 *  - overview / projects / locales / runs / quality → SOLO+ (paid plans)
 *  - members / cost-breakdown → TEAM only (Team plan analytics is the headline feature)
 *
 * FREE users get a 403 with an upgrade prompt; the page UI translates that to a
 * soft redirect with a toast. ENTERPRISE inherits TEAM access by ordinal.
 */
fun Route.configureAnalyticsRoutes(
    analyticsService: AnalyticsService,
    billingRepository: BillingRepository
) {
    route("/api/analytics") {

        get("/overview") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            call.requirePlan(BillingPlan.SOLO, billingRepository, userId) ?: return@get
            call.respond(analyticsService.overview(userId))
        }

        get("/projects") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            call.requirePlan(BillingPlan.SOLO, billingRepository, userId) ?: return@get
            val range = AnalyticsService.Range.parse(call.request.queryParameters["range"])
            call.respond(analyticsService.projects(userId, range))
        }

        get("/locales") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            call.requirePlan(BillingPlan.SOLO, billingRepository, userId) ?: return@get
            val range = AnalyticsService.Range.parse(call.request.queryParameters["range"])
            call.respond(analyticsService.locales(userId, range))
        }

        get("/runs") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            call.requirePlan(BillingPlan.SOLO, billingRepository, userId) ?: return@get
            val range = AnalyticsService.Range.parse(call.request.queryParameters["range"])
            val projectId = call.request.queryParameters["projectId"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
            call.respond(analyticsService.runs(userId, range, projectId, limit))
        }

        get("/quality") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            call.requirePlan(BillingPlan.SOLO, billingRepository, userId) ?: return@get
            call.respond(analyticsService.quality(userId))
        }

        get("/members") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            call.requirePlan(BillingPlan.TEAM, billingRepository, userId) ?: return@get
            val range = AnalyticsService.Range.parse(call.request.queryParameters["range"])
            call.respond(analyticsService.members(userId, range))
        }

        get("/cost-breakdown") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            call.requirePlan(BillingPlan.TEAM, billingRepository, userId) ?: return@get
            call.respond(analyticsService.costBreakdown(userId))
        }
    }
}
