package com.syncling.services

import com.syncling.domain.BillingPlan
import com.syncling.domain.UsageStats
import com.syncling.domain.UserEvent
import com.syncling.repository.BillingRepository
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

    suspend fun checkAndEnforceLimits(userId: String, stringsToTranslate: Int): Boolean {
        val subscription = billingRepository.getSubscription(userId)
        val plan = subscription.plan
        val usage = billingRepository.getUsage(userId)

        val stringLimit = plan.stringLimit
        if (stringLimit != null) {
            val projected = usage.stringsTranslated + stringsToTranslate
            if (projected > stringLimit) {
                recordLimitHit(subscription)
                val msg = if (plan == BillingPlan.FREE)
                    "Free plan limit of $stringLimit strings/month reached. Upgrade to continue translating."
                else
                    "Monthly string quota ($stringLimit) reached for the ${plan.displayName} plan. Upgrade for unlimited strings."
                throw IllegalStateException(msg)
            }
        }
        return true
    }

    /**
     * Atomically records usage and enforces the plan limit in one MongoDB operation.
     * Returns false if the limit would be exceeded — the caller should abort the run without
     * creating a PR. Use this AFTER translations are complete to replace the separate
     * recordUsage() call, eliminating the read-then-write race under concurrent pipelines.
     */
    suspend fun recordUsageAtomic(userId: String, stringsTranslated: Int): Boolean {
        val subscription = billingRepository.getSubscription(userId)
        val limit = subscription.plan.stringLimit ?: return run {
            billingRepository.recordUsage(userId, stringsTranslated)
            true
        }
        val ok = billingRepository.incrementUsageIfUnderLimit(userId, stringsTranslated, limit)
        if (!ok) {
            recordLimitHit(subscription)
        }
        return ok
    }

    // Marks limitHitAt so isLimitAlreadyExceeded() can fast-fail future webhooks without
    // running the full pipeline. Applies to all plans with a string limit, not just trials.
    private suspend fun recordLimitHit(subscription: com.syncling.domain.Subscription) {
        if (subscription.limitHitAt == null) {
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
