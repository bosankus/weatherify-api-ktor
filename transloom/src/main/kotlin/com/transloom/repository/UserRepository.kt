package com.transloom.repository

import com.transloom.domain.OnboardingStep
import com.transloom.domain.User
import kotlinx.datetime.Instant

interface UserRepository {
    /**
     * Returns the user after upsert and a flag indicating whether this call inserted
     * a new document (true) or matched an existing one (false). The flag drives the
     * SIGNED_UP vs LOGGED_IN distinction in activity tracking.
     */
    suspend fun upsert(
        githubId: Long,
        username: String,
        email: String?,
        avatarUrl: String?,
        githubToken: String?
    ): UpsertResult

    suspend fun findByGithubId(githubId: Long): User?

    suspend fun findById(userId: String): User?

    /** Batch lookup by id. Returns only users that exist; order is not guaranteed. */
    suspend fun findByIds(userIds: Collection<String>): List<User>

    /** Bumps lastActiveAt to now. Fire-and-forget; failures don't block requests. */
    suspend fun touchLastActive(userId: String, at: Instant)

    /**
     * Monotonically advances onboardingStep. If [step] is COMPLETED, stamps
     * onboardingCompletedAt as well. Earlier steps are ignored.
     */
    suspend fun advanceOnboarding(userId: String, step: OnboardingStep, at: Instant)

    /** Marks the in-product tour as dismissed. Idempotent. */
    suspend fun setOnboardingDismissed(userId: String, at: Instant)

    /** Clears the dismissed flag so the tour resumes on next dashboard load. */
    suspend fun clearOnboardingDismissed(userId: String)

    /** All users — only used by the background monitor; bounded by [limit] for safety. */
    suspend fun listAll(limit: Int = 5_000): List<User>

    /**
     * Returns users at SIGNED_UP or PROJECT_CREATED whose signupAt is older
     * than [signedUpBefore]. Replaces a full listAll scan for stuck-user detection.
     */
    suspend fun findStuckOnboarding(signedUpBefore: Instant): List<User>

    data class UpsertResult(val user: User, val isNewUser: Boolean)
}
