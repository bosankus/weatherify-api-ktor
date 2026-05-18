package com.transloom.domain

import kotlinx.datetime.Instant

data class User(
    val id: String,
    val githubId: Long,
    val githubUsername: String,
    val email: String?,
    val githubToken: String?,
    val avatarUrl: String?,
    /** First time we observed this user — set on initial OAuth, never updated. */
    val signupAt: Instant? = null,
    /** Touched on every authenticated request that flows through [touchLastActive]. */
    val lastActiveAt: Instant? = null,
    /** Furthest onboarding milestone reached; advances monotonically. */
    val onboardingStep: OnboardingStep = OnboardingStep.SIGNED_UP,
    val onboardingCompletedAt: Instant? = null,
)
