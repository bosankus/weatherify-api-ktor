package com.syncling.repository

import com.syncling.domain.ReviewerFeedback

interface ReviewerFeedbackRepository {
    /** Record one reviewer correction. No-op if [reviewerEdit] equals [modelOutput] (no learning signal). */
    suspend fun record(
        projectId: String,
        targetLanguage: String,
        sourceText: String,
        modelOutput: String,
        reviewerEdit: String,
        reason: String? = null
    )

    /**
     * Returns the most recent reviewer corrections for the given (project, locale), newest first.
     * The pipeline injects these as few-shot examples in the system prompt.
     */
    suspend fun recentExamples(projectId: String, targetLanguage: String, limit: Int = 5): List<ReviewerFeedback>

    /** Count corrections recorded across [projectIds] since [sinceMillis]. Used by the dashboard insights tile. */
    suspend fun countForProjectsSince(projectIds: Collection<String>, sinceMillis: Long): Int
}
