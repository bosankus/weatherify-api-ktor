package com.syncling.domain

/**
 * A pipeline run that was aborted because the owner's plan quota was exhausted.
 * One record per project (newest push wins) so the run can be re-enqueued
 * automatically the moment the quota constraint is lifted — plan upgrade,
 * trial activation, or the monthly usage reset.
 */
data class QuotaBlockedRun(
    val projectId: String,
    val ownerId: String,
    val repo: String,
    val branch: String,
    val commitHash: String,
    /** Run that surfaced the quota error on the dashboard; the resume run links back to it. */
    val originalRunId: String?,
    /** Strings awaiting translation when the run was blocked (0 = unknown, e.g. dropped webhook). */
    val stringsPending: Int,
    /** Target languages the pending strings would be translated into. */
    val languagesPending: Int,
    val blockedAt: Long
)
