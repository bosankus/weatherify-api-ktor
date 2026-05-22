package com.transloom.services

import com.transloom.domain.BillingPlan
import com.transloom.domain.UsageStats
import com.transloom.domain.UserEvent
import com.transloom.repository.BillingRepository
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

class BillingService(
    private val billingRepository: BillingRepository,
    private val userActivityService: UserActivityService? = null
) {
    private val log = LoggerFactory.getLogger(BillingService::class.java)

    suspend fun getPlan(userId: String): BillingPlan = billingRepository.getSubscription(userId).plan

    suspend fun subscribe(userId: String, plan: BillingPlan) {
        billingRepository.upsertSubscription(userId, plan)
    }

    /**
     * Cheap pre-check: returns true if the user has already hit their limit.
     * Call this BEFORE any expensive operations (GitHub API, DB queries) to fail fast.
     */
    suspend fun isLimitAlreadyExceeded(userId: String): Boolean =
        billingRepository.getSubscription(userId).limitHitAt != null

    suspend fun checkAndEnforceLimits(userId: String, stringsToTranslate: Int, currentProjects: Int): Boolean {
        val subscription = billingRepository.getSubscription(userId)
        val plan = subscription.plan
        val usage = billingRepository.getUsage(userId)

        if (plan.maxProjects <= currentProjects) {
            recordTrialLimitHit(subscription)
            val msg = if (plan == BillingPlan.FREE)
                "Free plan supports 1 project. Upgrade to Solo (3 projects) or Team (10 projects)."
            else
                "Project limit (${plan.maxProjects}) reached for the ${plan.displayName} plan."
            throw IllegalStateException(msg)
        }
        val stringLimit = plan.stringLimit
        if (stringLimit != null) {
            val projected = usage.stringsTranslated + stringsToTranslate
            if (projected > stringLimit) {
                recordTrialLimitHit(subscription)
                val msg = if (plan == BillingPlan.FREE)
                    "Free plan limit of $stringLimit strings/month reached. Upgrade to continue translating."
                else
                    "Monthly string quota ($stringLimit) reached for the ${plan.displayName} plan. Upgrade for unlimited strings."
                throw IllegalStateException(msg)
            }
        }
        return true
    }

    private suspend fun recordTrialLimitHit(subscription: com.transloom.domain.Subscription) {
        if (subscription.inTrial && subscription.limitHitAt == null) {
            billingRepository.setLimitHitAt(subscription.userId, Clock.System.now())
            userActivityService?.record(
                subscription.userId, UserEvent.TRIAL_LIMIT_HIT,
                mapOf("plan" to subscription.plan.name)
            )
        }
    }

    suspend fun recordUsage(userId: String, stringsTranslated: Int) {
        billingRepository.recordUsage(userId, stringsTranslated)
    }

    suspend fun getUsage(userId: String): UsageStats = billingRepository.getUsage(userId)
}
