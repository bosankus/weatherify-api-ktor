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

    suspend fun listPendingReviews(ownerId: String): List<Translation>

    suspend fun approve(translationId: String, editedText: String? = null): Boolean

    suspend fun reject(translationId: String, reason: String): Boolean

    suspend fun getTranslation(translationId: String): Translation?

    suspend fun countByStatus(ownerId: String): Map<String, Int>

    suspend fun totalStringsTranslated(ownerId: String): Int

    suspend fun activeLanguageCount(ownerId: String): Int

    suspend fun revertToReview(translationId: String)
}
