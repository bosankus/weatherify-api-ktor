package com.transloom.routes

import com.transloom.domain.OnboardingStep
import com.transloom.model.ApiError
import com.transloom.repository.BillingRepository
import com.transloom.repository.ProjectRepository
import com.transloom.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class OnboardingStateResponse(
    val step: String,
    val dismissed: Boolean,
    val completed: Boolean,
    val plan: String,
    val inTrial: Boolean,
    val trialLimitHit: Boolean,
    val hasProject: Boolean,
    /** True when the tour should be shown automatically on dashboard load. */
    val showTour: Boolean
)

/**
 * Drives the in-product onboarding tour. State is server-authoritative so the tour
 * survives reloads, incognito, and multi-device usage — never trust localStorage as
 * the source of truth for which step a user is on.
 */
fun Route.configureOnboardingRoutes(
    userRepository: UserRepository,
    billingRepository: BillingRepository,
    projectRepository: ProjectRepository
) {
    route("/transloom/api/onboarding") {

        get("/state") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val user = userRepository.findById(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("User not found"))

            val subscription = billingRepository.getSubscription(userId)
            val plan = subscription.plan.name
            val inTrial = subscription.inTrial
            val trialLimitHit = subscription.limitHitAt != null
            val hasProject = projectRepository.countForUser(userId) > 0
            val completed = user.onboardingStep == OnboardingStep.COMPLETED
            val dismissed = user.onboardingDismissedAt != null

            call.respond(
                HttpStatusCode.OK,
                OnboardingStateResponse(
                    step = user.onboardingStep.name,
                    dismissed = dismissed,
                    completed = completed,
                    plan = plan,
                    inTrial = inTrial,
                    trialLimitHit = trialLimitHit,
                    hasProject = hasProject,
                    showTour = !completed && !dismissed
                )
            )
        }

        post("/skip") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            userRepository.setOnboardingDismissed(userId, Clock.System.now())
            call.respond(HttpStatusCode.OK, mapOf("dismissed" to true))
        }

        post("/resume") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            userRepository.clearOnboardingDismissed(userId)
            call.respond(HttpStatusCode.OK, mapOf("dismissed" to false))
        }
    }
}
