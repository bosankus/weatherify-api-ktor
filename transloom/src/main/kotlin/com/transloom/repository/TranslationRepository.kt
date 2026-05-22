package com.transloom.repository

import com.transloom.domain.StringsPage
import com.transloom.domain.Translation
import com.transloom.domain.TranslationHistoryEntry

interface TranslationRepository {
    suspend fun upsertString(projectId: String, key: String, sourceText: String): String

    suspend fun upsertTranslation(
        stringId: String,
        targetLanguage: String,
        targetRegion: String?,
        translatedText: String,
        status: String,
        blockReason: String? = null,
        pipelineRunId: String? = null,
        commitShort: String? = null
    )

    suspend fun listStringsForProject(projectId: String, limit: Int = 100, offset: Int = 0): StringsPage

    suspend fun getStringKeysAndTexts(projectId: String): Map<String, String>

    suspend fun listPendingReviews(
        ownerId: String,
        limit: Int = 50,
        offset: Int = 0,
        language: String? = null,
        statusFilter: String? = null
    ): List<Translation>

    /** Total count of pending reviews — run in parallel with listPendingReviews for pagination. */
    suspend fun countPendingReviews(ownerId: String, language: String? = null, statusFilter: String? = null): Int

    suspend fun approve(translationId: String, editedText: String? = null): Boolean

    /** Bulk-approve a list of review-status translations. Returns the number actually modified. */
    suspend fun approveMany(translationIds: List<String>): Int

    suspend fun reject(translationId: String, reason: String): Boolean

    suspend fun getTranslation(translationId: String): Translation?

    suspend fun countByStatus(ownerId: String): Map<String, Int>

    suspend fun totalStringsTranslated(ownerId: String): Int

    suspend fun activeLanguageCount(ownerId: String): Int

    suspend fun revertToReview(translationId: String)

    /** Returns all translations with status "approved" (manually approved, awaiting follow-up PR) for a project. */
    suspend fun getApprovedForProject(projectId: String): List<Translation>

    /**
     * Atomically sets status "approved" → "auto" for the given IDs.
     * Returns the number of documents actually modified (0 if another coroutine already claimed them).
     */
    suspend fun claimApproved(translationIds: List<String>): Int

    /**
     * Returns all confirmed translations (status "auto" or "approved") for a project, used when
     * building a CDN locale bundle. Excludes "review" (unconfirmed) and "blocked" (rejected).
     */
    suspend fun getPublishableTranslations(projectId: String): List<Translation>

    /**
     * Fetches multiple translations in a single aggregation pass — use instead of
     * repeated getTranslation() calls to avoid N+1 database round-trips.
     */
    suspend fun listByIds(ids: List<String>): List<Translation>

    /** Locks a translation so the pipeline will never overwrite it. Returns false if not found. */
    suspend fun lock(translationId: String, lockedBy: String): Boolean

    /** Removes the lock from a translation, allowing future pipeline runs to update it. */
    suspend fun unlock(translationId: String): Boolean

    /** Returns the change history for a specific string key across all languages. */
    suspend fun listHistory(projectId: String, stringKey: String, limit: Int = 30): List<TranslationHistoryEntry>
}
