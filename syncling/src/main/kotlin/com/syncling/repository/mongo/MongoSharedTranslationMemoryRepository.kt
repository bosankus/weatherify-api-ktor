package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.repository.SharedTranslationMemoryRepository
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.security.MessageDigest
import java.util.UUID

class MongoSharedTranslationMemoryRepository(db: MongoDatabase) : SharedTranslationMemoryRepository {

    private val col = db.getCollection<Document>("shared_translation_memory")

    override suspend fun get(sourceText: String, targetLanguage: String): String? =
        col.find(eq("hashKey", hashKey(sourceText, targetLanguage)))
            .firstOrNull()
            ?.getString("translatedText")

    override suspend fun contribute(sourceText: String, targetLanguage: String, translatedText: String) {
        val now = System.currentTimeMillis()
        val key = hashKey(sourceText, targetLanguage)
        // Increment contributorCount and update translatedText on each contribution.
        // setOnInsert initialises the document; $inc on contributorCount naturally
        // accumulates the number of distinct projects that confirmed this translation.
        val update = Updates.combine(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("hashKey", key),
            Updates.setOnInsert("targetLanguage", targetLanguage),
            Updates.setOnInsert("createdAt", now),
            Updates.set("translatedText", translatedText),
            Updates.inc("contributorCount", 1),
            Updates.set("lastContributedAt", now)
        )
        col.findOneAndUpdate(eq("hashKey", key), update, FindOneAndUpdateOptions().upsert(true))
    }

    private fun hashKey(sourceText: String, targetLanguage: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$sourceText|$targetLanguage".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
