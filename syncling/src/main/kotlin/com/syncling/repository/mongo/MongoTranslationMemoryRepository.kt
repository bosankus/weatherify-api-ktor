package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.flow.firstOrNull
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.repository.TranslationMemoryRepository
import org.bson.Document
import java.util.UUID

class MongoTranslationMemoryRepository(db: MongoDatabase) : TranslationMemoryRepository {

    private val collection = db.getCollection<Document>("translation_memory")

    override suspend fun getTranslation(hashKey: String): String? {
        return collection.find(eq("hashKey", hashKey)).firstOrNull()?.getString("translatedText")
    }

    override suspend fun storeTranslation(hashKey: String, translatedText: String) {
        val now = System.currentTimeMillis()
        val update = Updates.combine(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("hashKey", hashKey),
            Updates.setOnInsert("createdAt", now),
            Updates.setOnInsert("usedCount", 1),
            Updates.set("translatedText", translatedText),
            Updates.set("lastUsedAt", now)
        )
        collection.findOneAndUpdate(eq("hashKey", hashKey), update, FindOneAndUpdateOptions().upsert(true))
    }

    override suspend fun incrementUsage(hashKey: String) {
        collection.updateOne(
            eq("hashKey", hashKey),
            Updates.combine(Updates.inc("usedCount", 1), Updates.set("lastUsedAt", System.currentTimeMillis()))
        )
    }
}
