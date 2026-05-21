package com.transloom.domain

import kotlinx.datetime.Instant

/** Categorical events recorded into the `user_events` collection. Never rename or remove a variant — it breaks historical analytics. */
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
    PIPELINE_RETRIED,             // user triggered manual retry on a failed run
    CHECKOUT_ABANDONED,           // monitor: SUBSCRIPTION_INITIATED > 60min, no payment completed
    ONBOARDING_STUCK,             // monitor: user stalled at an onboarding step past threshold
}

/** Onboarding milestones denormalised on the User document. Always advances forward via [advance]. */
enum class OnboardingStep {
    SIGNED_UP,
    PROJECT_CREATED,
    WEBHOOK_INSTALLED,
    FIRST_TRANSLATION,
    PLAN_ACTIVATED,
    COMPLETED;

    fun advance(to: OnboardingStep): OnboardingStep = if (to.ordinal > this.ordinal) to else this
}

data class UserActivity(
    val id: String,
    val userId: String,
    val event: UserEvent,
    val occurredAt: Instant,
    val metadata: Map<String, String> = emptyMap()
)

/** Computed at request time from user, subscription, and activity — never persisted. */
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
