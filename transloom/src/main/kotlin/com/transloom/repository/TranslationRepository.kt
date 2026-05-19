package com.transloom.repository

import com.transloom.domain.StringsPage
import com.transloom.domain.Translation

interface TranslationRepository {
    suspend fun upsertString(projectId: String, key: String, sourceText: String): String

    suspend fun upsertTranslation(
        stringId: String,
        targetLanguage: String,
        targetRegion: String?,
        translatedText: String,
        status: String,
        blockReason: String? = null
    )

    suspend fun listStringsForProject(projectId: String, limit: Int = 100, offset: Int = 0): StringsPage

    suspend fun getStringKeysAndTexts(projectId: String): Map<String, String>

    suspend fun listPendingReviews(ownerId: String, limit: Int = 50, offset: Int = 0): List<Translation>

    /** Total count of pending reviews for the user — run in parallel with listPendingReviews for pagination. */
    suspend fun countPendingReviews(ownerId: String): Int

    suspend fun approve(translationId: String, editedText: String? = null): Boolean

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
}
