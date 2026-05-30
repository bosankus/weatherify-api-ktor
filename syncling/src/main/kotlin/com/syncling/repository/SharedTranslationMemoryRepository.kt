package com.syncling.repository

interface SharedTranslationMemoryRepository {
    /** Returns a cached translation if at least one project has contributed it. */
    suspend fun get(sourceText: String, targetLanguage: String): String?

    /** Records a confirmed auto-approved translation into the shared pool. */
    suspend fun contribute(sourceText: String, targetLanguage: String, translatedText: String)
}
