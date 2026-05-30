package com.syncling.domain

/**
 * Monthly per-member rollup of translation activity for a single project.
 *
 * Keyed by (projectId, memberUserId, yearMonth). `memberUserId` is the literal
 * sentinel "external" for runs where the GitHub commit author couldn't be
 * matched to any active project member — those still increment the owner's
 * billing in [BillingRepository.recordUsage] but are surfaced distinctly in
 * Team-plan analytics rather than silently rolled up to the owner.
 *
 * `perLocale` is denormalized so the analytics tab can render per-locale
 * breakdowns without joining against the translations collection.
 */
data class MemberUsage(
    val projectId: String,
    val memberUserId: String,
    val ownerId: String,
    val yearMonth: String,
    val stringsTranslated: Int,
    val runsTriggered: Int,
    val perLocale: Map<String, Int>
) {
    companion object {
        const val EXTERNAL = "external"
    }
}

/**
 * Persistent summary of a pipeline run. Lives in Mongo with a 365-day TTL on
 * [startedAt] so the analytics tab has history across months, complementing
 * the Redis-backed live state which expires after 24h.
 */
data class PipelineRunSummary(
    val runId: String,
    val projectId: String,
    val ownerId: String,
    val triggeredByUserId: String?,
    val triggeredByLabel: String,
    val repo: String,
    val branch: String,
    val commitShort: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val durationMs: Long?,
    val status: String,            // succeeded | failed | partial
    val stringsTranslated: Int,
    val stringsPerLocale: Map<String, Int>,
    val error: String?,
    /** Number of strings served from translation memory cache (not billed, not Gemini calls). */
    val cacheHits: Int = 0
)
