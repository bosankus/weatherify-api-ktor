package com.syncling.services

import com.syncling.domain.BillingPlan
import com.syncling.domain.UsageStats
import com.syncling.domain.UserEvent
import com.syncling.repository.BillingRepository
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Thrown when a run would exceed the owner's plan-level string quota (as opposed to a
 * per-project cap). Carries the counts so the pipeline can record a resumable blocked
 * run and tell the user exactly how much work is waiting.
 */
class PlanLimitExceededException(message: String, val stringsPending: Int) : IllegalStateException(message)

class BillingService(
    private val billingRepository: BillingRepository,
    private val userActivityService: UserActivityService? = null
) {
    private val log = LoggerFactory.getLogger(BillingService::class.java)

    companion object {
        const val PAYMENT_PENDING_MESSAGE =
            "Subscription payment failed — complete the pending payment to resume translations."
    }

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

    /**
     * Cheap pre-check returning a user-facing reason translations are blocked, or null when
     * the account is in good standing. A payment-failed hold takes priority over quota.
     */
    suspend fun accessBlockReason(userId: String): String? {
        val subscription = billingRepository.getSubscription(userId)
        return when {
            subscription.paymentPending -> PAYMENT_PENDING_MESSAGE
            subscription.limitHitAt != null ->
                "Monthly string quota reached — upgrade your plan to resume translations."
            else -> null
        }
    }

    suspend fun checkAndEnforceLimits(userId: String, stringsToTranslate: Int): Boolean {
        val subscription = billingRepository.getSubscription(userId)
        if (subscription.paymentPending) throw IllegalStateException(PAYMENT_PENDING_MESSAGE)
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
                throw PlanLimitExceededException(msg, stringsToTranslate)
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
