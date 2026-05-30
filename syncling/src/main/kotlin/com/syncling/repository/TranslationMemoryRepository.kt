package com.syncling.repository

interface TranslationMemoryRepository {
    suspend fun getTranslation(hashKey: String): String?

    suspend fun storeTranslation(hashKey: String, translatedText: String)

    suspend fun incrementUsage(hashKey: String)
}
