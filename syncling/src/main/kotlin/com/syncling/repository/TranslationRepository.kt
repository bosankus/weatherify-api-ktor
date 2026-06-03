package com.syncling.repository

import com.syncling.domain.StringsPage
import com.syncling.domain.Translation
import com.syncling.domain.TranslationHistoryEntry

/**
 * One translation document to be upserted by [TranslationRepository.bulkUpsertTranslations].
 * Field semantics match [TranslationRepository.upsertTranslation].
 */
data class TranslationUpsert(
    val stringId: String,
    val projectId: String,
    val ownerId: String,
    val stringKey: String,
    val sourceText: String,
    val projectName: String,
    val targetLanguage: String,
    val targetRegion: String?,
    val translatedText: String,
    val status: String,
    val blockReason: String? = null,
    val pipelineRunId: String? = null,
    val commitShort: String? = null
)

interface TranslationRepository {
    suspend fun upsertString(projectId: String, key: String, sourceText: String): String

    suspend fun upsertTranslation(
        stringId: String,
        projectId: String,
        ownerId: String,
        stringKey: String,
        sourceText: String,
        projectName: String,
        targetLanguage: String,
        targetRegion: String?,
        translatedText: String,
        status: String,
        blockReason: String? = null,
        pipelineRunId: String? = null,
        commitShort: String? = null
    )

    /**
     * Atomic bulk upsert of source strings keyed by [keyToSource]. Replaces N round trips
     * with a single unordered bulkWrite. Returns the stringId for every input key — stable
     * across calls (existing IDs reused, new keys get fresh UUIDs). When a key's sourceText
     * differs from the persisted value, the change fans out to denormalized translation docs
     * in a second unordered bulkWrite (preserving the [upsertString] contract).
     */
    suspend fun bulkUpsertStrings(projectId: String, keyToSource: Map<String, String>): Map<String, String>

    /**
     * Atomic bulk upsert of translation documents. Replaces N · M round trips with a single
     * unordered bulkWrite per call. Honours per-translation locks (locked rows are skipped),
     * captures previousTranslatedText diffs, and writes the same translation_history audit
     * entries as [upsertTranslation].
     */
    suspend fun bulkUpsertTranslations(upserts: List<TranslationUpsert>)

    /** Backfills denormalized fields (stringKey, sourceText, projectId, ownerId, projectName) on translations docs that are missing them. Safe to run repeatedly. */
    suspend fun backfillDenormalizedFields(): Int

    suspend fun listStringsForProject(projectId: String, limit: Int = 100, offset: Int = 0): StringsPage

    suspend fun getStringKeysAndTexts(projectId: String): Map<String, String>

    /**
     * Returns the set of string keys that have at least one translation entry for the given project.
     * Used to detect "orphaned" source strings — stored by a previously failed pipeline run but
     * never translated — so they can be re-processed on the next run rather than silently skipped.
     */
    suspend fun getProcessedStringKeys(projectId: String): Set<String>

    suspend fun listPendingReviews(
        ownerId: String,
        limit: Int = 50,
        offset: Int = 0,
        language: String? = null,
        statusFilter: String? = null
    ): List<Translation>

    /** Total count of pending reviews — run in parallel with listPendingReviews for pagination. */
    suspend fun countPendingReviews(ownerId: String, language: String? = null, statusFilter: String? = null): Int

    /**
     * Like [listPendingReviews] but scoped to an explicit set of project IDs rather than
     * owner-derived projects. Use when the caller already resolved the access-controlled
     * set (e.g. owned projects ∪ member projects for the review portal).
     */
    suspend fun listPendingReviewsForProjects(
        projectIds: List<String>,
        limit: Int = 50,
        offset: Int = 0,
        language: String? = null,
        statusFilter: String? = null
    ): List<Translation>

    /** Pair of [listPendingReviewsForProjects] — run in parallel for pagination. */
    suspend fun countPendingReviewsForProjects(
        projectIds: List<String>,
        language: String? = null,
        statusFilter: String? = null
    ): Int

    suspend fun approve(translationId: String, editedText: String? = null): Boolean

    /** Bulk-approve a list of review-status translations. Returns the number actually modified. */
    suspend fun approveMany(translationIds: List<String>): Int

    /**
     * Overwrites a translation's text and sets status to "auto" so it's immediately publishable
     * to the CDN. Used by the OTA hotfix flow — bypasses the manual-review and follow-up-PR cycle.
     */
    suspend fun hotfix(translationId: String, newText: String): Boolean

    suspend fun reject(translationId: String, reason: String): Boolean

    suspend fun getTranslation(translationId: String): Translation?

    suspend fun countByStatus(ownerId: String): Map<String, Int>

    /** Per-project status counts. Returns at minimum {auto, review, blocked} — missing keys are zero. */
    suspend fun countByStatusForProject(projectId: String): Map<String, Int>

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
