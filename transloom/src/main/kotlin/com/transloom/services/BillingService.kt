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

    suspend fun checkAndEnforceLimits(userId: String, stringsToTranslate: Int, currentProjects: Int): Boolean {
        val subscription = billingRepository.getSubscription(userId)
        val plan = subscription.plan
        val usage = billingRepository.getUsage(userId)

        if (plan.maxProjects <= currentProjects) {
            recordTrialLimitHit(subscription)
            throw IllegalStateException("Project limit exceeded for plan ${plan.name}. Upgrade to add more projects.")
        }
        val stringLimit = plan.stringLimit
        if (stringLimit != null) {
            val projected = usage.stringsTranslated + stringsToTranslate
            if (projected > stringLimit) {
                recordTrialLimitHit(subscription)
                throw IllegalStateException("Monthly string limit ($stringLimit) exceeded for plan ${plan.name}. Please upgrade.")
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
