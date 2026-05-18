package com.transloom.routes

import com.transloom.domain.UserActivity
import com.transloom.domain.UserInsights
import com.transloom.model.ApiError
import com.transloom.services.UserActivityService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ActivityEntry(
    val event: String,
    val occurredAt: String,
    val metadata: Map<String, String>
)

@Serializable
data class UserInsightsResponse(
    val onboardingStep: String,
    val onboardingCompletedAt: String?,
    val signupAt: String?,
    val lastActiveAt: String?,
    val daysSinceLastActivity: Long?,
    val plan: String,
    val planExpiresAt: String?,
    val daysUntilPlanExpiry: Long?,
    val inTrial: Boolean,
    val trialLimitHit: Boolean,
    val totalEvents: Int,
    val recentEvents: List<ActivityEntry>,
    val suggestedActions: List<String>,
    val isStuck: Boolean,
    val stuckReason: String?
)

/**
 * Per-user lifecycle endpoint that powers the dashboard's "Your account" panel.
 * Suggestions returned here are designed to be shown verbatim as dashboard banners.
 */
fun Route.configureInsightsRoutes(userActivityService: UserActivityService) {
    route("/transloom/api/insights") {

        get("/me") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val insights = userActivityService.insightsFor(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("User not found"))
            call.respond(HttpStatusCode.OK, insights.toResponse())
        }

        get("/me/activity") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val insights = userActivityService.insightsFor(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("User not found"))
            call.respond(
                HttpStatusCode.OK,
                mapOf("activity" to insights.recentEvents.map { it.toEntry() })
            )
        }
    }
}

private fun UserInsights.toResponse() = UserInsightsResponse(
    onboardingStep = onboardingStep.name,
    onboardingCompletedAt = onboardingCompletedAt?.toString(),
    signupAt = signupAt?.toString(),
    lastActiveAt = lastActiveAt?.toString(),
    daysSinceLastActivity = daysSinceLastActivity,
    plan = plan,
    planExpiresAt = planExpiresAt?.toString(),
    daysUntilPlanExpiry = daysUntilPlanExpiry,
    inTrial = inTrial,
    trialLimitHit = trialLimitHit,
    totalEvents = totalEvents,
    recentEvents = recentEvents.map { it.toEntry() },
    suggestedActions = suggestedActions,
    isStuck = isStuck,
    stuckReason = stuckReason
)

private fun UserActivity.toEntry() = ActivityEntry(
    event = event.name,
    occurredAt = occurredAt.toString(),
    metadata = metadata
)
