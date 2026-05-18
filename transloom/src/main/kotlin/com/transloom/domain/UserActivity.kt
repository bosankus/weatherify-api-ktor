package com.transloom.domain

import kotlinx.datetime.Instant

/**
 * Categorical user events captured into the `user_events` MongoDB collection.
 * The set is intentionally small and stable — adding a new variant is fine,
 * but renaming or removing one will silently break historical analytics.
 *
 * Recorded at the *boundary* of state changes (auth callback, payment confirm,
 * webhook handler) rather than inside business logic, so the activity stream
 * is a faithful audit trail of what actually happened on the platform.
 */
enum class UserEvent {
    SIGNED_UP,                    // first OAuth callback success
    LOGGED_IN,                    // returning OAuth callback (existing user)
    PROJECT_CREATED,
    PROJECT_DELETED,
    WEBHOOK_INSTALLED,
    TRANSLATION_RUN_STARTED,
    SUBSCRIPTION_INITIATED,       // Razorpay sub created, user has not paid yet
    PAYMENT_VERIFIED,             // Checkout.js handler signature verified
    SUBSCRIPTION_AUTHENTICATED,   // webhook: card saved during trial
    SUBSCRIPTION_CHARGED,         // webhook: real money moved
    SUBSCRIPTION_CANCELLED,
    PLAN_ACTIVATED,               // pending → active plan promotion
    INVOICE_GENERATED,
    INVOICE_DOWNLOADED,
    TRIAL_LIMIT_HIT,
    PLAN_EXPIRY_NOTIFIED,         // monitor flagged user for upcoming renewal
}

/**
 * Onboarding milestones — denormalised onto the User document so the dashboard
 * can answer "who's stuck where?" without aggregating across user_events.
 * Always advances forward; we never roll it back.
 */
enum class OnboardingStep {
    SIGNED_UP,
    PROJECT_CREATED,
    WEBHOOK_INSTALLED,
    FIRST_TRANSLATION,
    PLAN_ACTIVATED,
    COMPLETED;

    /** Higher ordinal = further along. Use [advance] to monotonically move forward. */
    fun advance(to: OnboardingStep): OnboardingStep = if (to.ordinal > this.ordinal) to else this
}

data class UserActivity(
    val id: String,
    val userId: String,
    val event: UserEvent,
    val occurredAt: Instant,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Read model returned by the insights endpoint. Computed at request time from
 * the user document, subscription, and recent activity — never persisted.
 */
data class UserInsights(
    val userId: String,
    val onboardingStep: OnboardingStep,
    val onboardingCompletedAt: Instant?,
    val signupAt: Instant?,
    val lastActiveAt: Instant?,
    val daysSinceLastActivity: Long?,
    val plan: String,
    val planExpiresAt: Instant?,
    val daysUntilPlanExpiry: Long?,
    val inTrial: Boolean,
    val trialLimitHit: Boolean,
    val totalEvents: Int,
    val recentEvents: List<UserActivity>,
    /** Human-readable nudges the dashboard can surface as banners ("Connect your first repo"). */
    val suggestedActions: List<String>,
    /** True when a heuristic believes the user got stuck — used by the monitor + dashboards. */
    val isStuck: Boolean,
    val stuckReason: String?
)
