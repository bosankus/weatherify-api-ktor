package com.syncling.routes

import com.syncling.domain.OnboardingStep
import com.syncling.model.ApiError
import com.syncling.repository.BillingRepository
import com.syncling.repository.ProjectRepository
import com.syncling.repository.TranslationRepository
import com.syncling.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    projectRepository: ProjectRepository,
    translationRepository: TranslationRepository
) {
    route("/transloom/api/onboarding") {

        get("/state") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val user = userRepository.findById(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("User not found"))

            // Read all needed data in parallel
            val (subscription, projectCount, stringsTranslated) = coroutineScope {
                val subD = async { billingRepository.getSubscription(userId) }
                val projD = async { projectRepository.countForUser(userId) }
                val strD  = async { translationRepository.totalStringsTranslated(userId) }
                Triple(subD.await(), projD.await(), strD.await())
            }

            // Auto-advance onboarding based on actual observed state rather than requiring
            // every code path to explicitly call userActivityService.record(). This means
            // users who e.g. set up a project via API but missed the WEBHOOK_INSTALLED event
            // still see the correct step on next dashboard load.
            val webhookVerified = projectRepository.listForUser(userId).any { it.webhookVerifiedAt != null }
            val autoStep = when {
                user.onboardingStep.ordinal >= OnboardingStep.COMPLETED.ordinal     -> user.onboardingStep
                subscription.plan.name != "FREE" || subscription.inTrial           -> user.onboardingStep.advance(OnboardingStep.PLAN_ACTIVATED)
                stringsTranslated > 0                                               -> user.onboardingStep.advance(OnboardingStep.FIRST_TRANSLATION)
                webhookVerified                                                     -> user.onboardingStep.advance(OnboardingStep.WEBHOOK_INSTALLED)
                projectCount > 0                                                    -> user.onboardingStep.advance(OnboardingStep.PROJECT_CREATED)
                else                                                                -> user.onboardingStep
            }
            if (autoStep != user.onboardingStep) {
                runCatching { userRepository.advanceOnboarding(userId, autoStep, Clock.System.now()) }
            }
            val effectiveStep = if (autoStep.ordinal > user.onboardingStep.ordinal) autoStep else user.onboardingStep

            val completed = effectiveStep == OnboardingStep.COMPLETED
            val dismissed = user.onboardingDismissedAt != null

            call.respond(
                HttpStatusCode.OK,
                OnboardingStateResponse(
                    step = effectiveStep.name,
                    dismissed = dismissed,
                    completed = completed,
                    plan = subscription.plan.name,
                    inTrial = subscription.inTrial,
                    trialLimitHit = subscription.limitHitAt != null,
                    hasProject = projectCount > 0,
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
