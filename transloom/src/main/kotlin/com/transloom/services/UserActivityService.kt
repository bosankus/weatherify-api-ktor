package com.transloom.services

import com.transloom.domain.BillingPlan
import com.transloom.domain.OnboardingStep
import com.transloom.domain.User
import com.transloom.domain.UserActivity
import com.transloom.domain.UserEvent
import com.transloom.domain.UserInsights
import com.transloom.repository.BillingRepository
import com.transloom.repository.ProjectRepository
import com.transloom.repository.UserActivityRepository
import com.transloom.repository.UserRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class UserActivityService(
    private val userRepository: UserRepository,
    private val userActivityRepository: UserActivityRepository,
    private val billingRepository: BillingRepository,
    private val projectRepository: ProjectRepository,
) {
    private val log = LoggerFactory.getLogger(UserActivityService::class.java)

    private val STUCK_AFTER_SIGNUP_HOURS = 48
    private val STUCK_AFTER_PROJECT_HOURS = 72
    private val PLAN_EXPIRING_SOON_DAYS = 5L
    private val PAYMENT_ABANDONED_MINUTES = 60L

    suspend fun record(
        userId: String,
        event: UserEvent,
        metadata: Map<String, String> = emptyMap()
    ): UserActivity {
        val now = Clock.System.now()
        val activity = userActivityRepository.record(userId, event, metadata)
        runCatching { userRepository.touchLastActive(userId, now) }
            .onFailure { log.warn("touchLastActive failed for userId={}: {}", userId, it.message) }
        onboardingStepFor(event)?.let { step ->
            runCatching { userRepository.advanceOnboarding(userId, step, now) }
                .onFailure { log.warn("advanceOnboarding failed for userId={}: {}", userId, it.message) }
        }
        log.debug("Activity recorded: userId={} event={} meta={}", userId, event, metadata)
        return activity
    }

    suspend fun insightsFor(userId: String): UserInsights? {
        val user = userRepository.findById(userId) ?: return null
        val subscription = billingRepository.getSubscription(userId)
        val recent = userActivityRepository.listForUser(userId, limit = 20)
        val totalEvents = userActivityRepository.countForUser(userId)
        val now = Clock.System.now()

        val daysSinceLastActivity = user.lastActiveAt?.let {
            (now.toEpochMilliseconds() - it.toEpochMilliseconds()) / (1000 * 60 * 60 * 24)
        }
        val daysUntilExpiry = subscription.currentPeriodEnd?.let {
            (it.toEpochMilliseconds() - now.toEpochMilliseconds()) / (1000 * 60 * 60 * 24)
        }

        val (stuck, stuckReason) = evaluateStuck(user, recent, now)
        val suggestions = buildSuggestions(user, subscription.plan, daysUntilExpiry, recent)

        return UserInsights(
            userId = user.id,
            onboardingStep = user.onboardingStep,
            onboardingCompletedAt = user.onboardingCompletedAt,
            signupAt = user.signupAt,
            lastActiveAt = user.lastActiveAt,
            daysSinceLastActivity = daysSinceLastActivity,
            plan = subscription.plan.name,
            planExpiresAt = subscription.currentPeriodEnd,
            daysUntilPlanExpiry = daysUntilExpiry,
            inTrial = subscription.inTrial,
            trialLimitHit = subscription.inTrial && subscription.limitHitAt != null,
            totalEvents = totalEvents,
            recentEvents = recent,
            suggestedActions = suggestions,
            isStuck = stuck,
            stuckReason = stuckReason
        )
    }

    // ─── Lifecycle scans (consumed by the monitor + admin endpoints) ─────────

    data class StuckUser(val user: User, val reason: String)
    data class ExpiringPlan(val user: User, val daysLeft: Long, val plan: BillingPlan)

    suspend fun findStuckUsers(): List<StuckUser> {
        val now = Clock.System.now()
        // Query only users who are at early onboarding steps AND signed up long enough ago
        // to be considered stuck. Avoids loading all users into memory.
        val threshold = now - STUCK_AFTER_SIGNUP_HOURS.hours
        return userRepository.findStuckOnboarding(threshold).mapNotNull { u ->
            val (stuck, reason) = evaluateStuck(u, emptyList(), now)
            if (stuck && reason != null) StuckUser(u, reason) else null
        }
    }

    suspend fun findExpiringPlans(): List<ExpiringPlan> {
        val now = Clock.System.now()
        // Query subscriptions directly by period-end window — no full user scan, no N+1.
        val windowEnd = now + PLAN_EXPIRING_SOON_DAYS.days
        val expiring = billingRepository.findExpiringSubscriptions(from = now, to = windowEnd)
        return expiring.mapNotNull { sub ->
            val user = userRepository.findById(sub.userId) ?: return@mapNotNull null
            val end = sub.currentPeriodEnd ?: return@mapNotNull null
            val daysLeft = (end.toEpochMilliseconds() - now.toEpochMilliseconds()) / (1000 * 60 * 60 * 24)
            ExpiringPlan(user, daysLeft, sub.plan)
        }
    }

    suspend fun findAbandonedPayments(): List<User> {
        val cutoffMs = PAYMENT_ABANDONED_MINUTES * 60 * 1000
        return userActivityRepository.findUsersWithAbandonedPayments(cutoffMs)
            .mapNotNull { userRepository.findById(it) }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun onboardingStepFor(event: UserEvent): OnboardingStep? = when (event) {
        UserEvent.SIGNED_UP -> OnboardingStep.SIGNED_UP
        UserEvent.PROJECT_CREATED -> OnboardingStep.PROJECT_CREATED
        UserEvent.WEBHOOK_INSTALLED -> OnboardingStep.WEBHOOK_INSTALLED
        UserEvent.TRANSLATION_RUN_STARTED -> OnboardingStep.FIRST_TRANSLATION
        UserEvent.PLAN_ACTIVATED, UserEvent.SUBSCRIPTION_CHARGED -> OnboardingStep.PLAN_ACTIVATED
        UserEvent.INVOICE_GENERATED -> OnboardingStep.COMPLETED
        else -> null
    }

    private fun evaluateStuck(user: User, recent: List<UserActivity>, now: Instant): Pair<Boolean, String?> {
        val signedUpAgo = user.signupAt?.let { now - it } ?: return false to null
        return when (user.onboardingStep) {
            OnboardingStep.SIGNED_UP ->
                if (signedUpAgo > STUCK_AFTER_SIGNUP_HOURS.hours)
                    true to "Signed up ${signedUpAgo.inWholeHours}h ago, no project yet"
                else false to null
            OnboardingStep.PROJECT_CREATED ->
                if (signedUpAgo > STUCK_AFTER_PROJECT_HOURS.hours)
                    true to "Project created but webhook not installed after ${signedUpAgo.inWholeHours}h"
                else false to null
            else -> false to null
        }
    }

    private fun buildSuggestions(
        user: User,
        plan: BillingPlan,
        daysUntilExpiry: Long?,
        recent: List<UserActivity>
    ): List<String> = buildList {
        when (user.onboardingStep) {
            OnboardingStep.SIGNED_UP ->
                add("Create your first project — point Transloom at a GitHub repo to start translating.")
            OnboardingStep.PROJECT_CREATED ->
                add("Install the GitHub webhook so commits to your watch branch auto-trigger translations.")
            OnboardingStep.WEBHOOK_INSTALLED ->
                add("Push a commit that changes a strings file to see the pipeline in action.")
            OnboardingStep.FIRST_TRANSLATION ->
                if (plan == BillingPlan.FREE)
                    add("Upgrade to Solo to lift the 500-string monthly cap and unlock unlimited languages.")
            else -> {}
        }
        if (daysUntilExpiry != null && daysUntilExpiry in 0..PLAN_EXPIRING_SOON_DAYS) {
            add("Your ${plan.displayName} plan renews in $daysUntilExpiry day(s) — confirm your card is up to date.")
        }
        val lastPaymentVerified = recent.firstOrNull { it.event == UserEvent.PAYMENT_VERIFIED }
        val lastInvoice = recent.firstOrNull { it.event == UserEvent.INVOICE_GENERATED }
        if (lastPaymentVerified != null && lastInvoice == null) {
            add("Your last payment is confirmed — your invoice will be available shortly.")
        }
    }

}
