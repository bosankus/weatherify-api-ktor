package com.syncling.services

import com.syncling.domain.BillingPlan
import com.syncling.domain.OnboardingStep
import com.syncling.domain.Subscription
import com.syncling.domain.UsageStats
import com.syncling.domain.User
import com.syncling.domain.UserActivity
import com.syncling.domain.UserEvent
import com.syncling.domain.UserInsights
import com.syncling.repository.BillingRepository
import com.syncling.repository.ProjectRepository
import com.syncling.repository.UserActivityRepository
import com.syncling.repository.UserRepository
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
    private val eventBus: PipelineEventBus? = null,
    private val notificationService: NotificationService? = null,
    private val inAppNotificationService: InAppNotificationService? = null
) {
    private val log = LoggerFactory.getLogger(UserActivityService::class.java)

    private val STUCK_AFTER_SIGNUP_HOURS = 48
    private val STUCK_AFTER_PROJECT_HOURS = 72
    private val STUCK_AFTER_WEBHOOK_HOURS = 24
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
                .onSuccess { eventBus?.emitOnboardingStep(userId, step.name) }
                .onFailure { log.warn("advanceOnboarding failed for userId={}: {}", userId, it.message) }
        }
        // Fire transactional emails for key lifecycle events.
        if (notificationService != null) {
            runCatching {
                when (event) {
                    UserEvent.TRIAL_LIMIT_HIT -> {
                        val user = userRepository.findById(userId)
                        if (user != null) notificationService?.sendTrialLimitHit(user)
                        inAppNotificationService?.notifyTrialLimitHit(userId)
                    }
                    else -> { /* other events handled by UserLifecycleMonitor scans */ }
                }
            }.onFailure { log.warn("Notification side-effect failed userId={} event={}: {}", userId, event, it.message) }
        }
        log.debug("Activity recorded: userId={} event={} meta={}", userId, event, metadata)
        return activity
    }

    /** Returns the pending (not yet activated) billing plan for a user, or null. */
    suspend fun getPendingPlan(userId: String): BillingPlan? =
        billingRepository.getSubscription(userId).pendingPlan

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

        val usage = billingRepository.getUsage(userId)
        val (stuck, stuckReason) = evaluateStuck(user, recent, now)
        val suggestions = buildSuggestions(user, subscription, daysUntilExpiry, recent, usage)

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

    /** Returns true if no event in [events] has been recorded for [userId] within [withinHours] hours. Used by the lifecycle monitor for dedup. */
    suspend fun shouldNotify(userId: String, events: Set<UserEvent>, withinHours: Long): Boolean {
        val last = userActivityRepository.lastOccurrence(userId, events) ?: return true
        return (Clock.System.now() - last.occurredAt).inWholeHours >= withinHours
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
            OnboardingStep.WEBHOOK_INSTALLED -> {
                val elapsed = user.lastActiveAt?.let { now - it } ?: signedUpAgo
                val hasTranslation = recent.any { it.event == UserEvent.TRANSLATION_RUN_STARTED }
                if (!hasTranslation && elapsed > STUCK_AFTER_WEBHOOK_HOURS.hours)
                    true to "Webhook installed but no translation run after ${elapsed.inWholeHours}h"
                else false to null
            }
            else -> false to null
        }
    }

    private fun buildSuggestions(
        user: User,
        subscription: Subscription,
        daysUntilExpiry: Long?,
        recent: List<UserActivity>,
        usage: UsageStats
    ): List<String> = buildList {
        // Highest urgency: trial limit hit — surface as sole action to maximise upgrade conversion.
        if (subscription.inTrial && subscription.limitHitAt != null) {
            add("You've hit your ${subscription.plan.displayName} trial limit — upgrade now to resume translations instantly.")
            return@buildList
        }

        when (user.onboardingStep) {
            OnboardingStep.SIGNED_UP ->
                add("Create your first project — point Syncling at a GitHub repo to start translating.")
            OnboardingStep.PROJECT_CREATED ->
                add("Install the GitHub webhook so commits to your watch branch auto-trigger translations.")
            OnboardingStep.WEBHOOK_INSTALLED ->
                add("Push a commit that changes a strings file — within seconds you'll see translated PRs appear in your dashboard.")
            OnboardingStep.FIRST_TRANSLATION ->
                if (subscription.plan == BillingPlan.FREE) {
                    val limit = BillingPlan.FREE.stringLimit ?: 500
                    val pct = if (limit > 0) (usage.stringsTranslated * 100) / limit else 0
                    add("You've used ${usage.stringsTranslated} / $limit strings this month ($pct%). Upgrade to Solo (₹499/mo) for 10× more strings and unlimited languages.")
                }
            else -> {}
        }

        if (daysUntilExpiry != null && daysUntilExpiry in 0..PLAN_EXPIRING_SOON_DAYS) {
            add("Your ${subscription.plan.displayName} plan renews in $daysUntilExpiry day(s) — confirm your card is up to date.")
        }
        val lastPaymentVerified = recent.firstOrNull { it.event == UserEvent.PAYMENT_VERIFIED }
        val lastInvoice = recent.firstOrNull { it.event == UserEvent.INVOICE_GENERATED }
        if (lastPaymentVerified != null && lastInvoice == null) {
            add("Your last payment is confirmed — your invoice will be available shortly.")
        }
    }

}
