package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.ChangeType
import com.transloom.domain.SemanticChangeRecord
import com.transloom.repository.SemanticChangeCacheRepository
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document

class MongoSemanticChangeCacheRepository(db: MongoDatabase) : SemanticChangeCacheRepository {

    private val col = db.getCollection<Document>("semantic_change_cache")

    override suspend fun get(hashKey: String): SemanticChangeRecord? {
        val doc = col.find(eq("_id", hashKey)).firstOrNull() ?: return null
        val changeType = runCatching { ChangeType.valueOf(doc.getString("changeType") ?: "") }.getOrNull()
            ?: return null
        return SemanticChangeRecord(changeType, doc.getString("reasoning") ?: "")
    }

    override suspend fun put(hashKey: String, record: SemanticChangeRecord) {
        col.findOneAndUpdate(
            eq("_id", hashKey),
            Updates.combine(
                Updates.setOnInsert("_id", hashKey),
                Updates.set("changeType", record.changeType.name),
                Updates.set("reasoning", record.reasoning),
                Updates.set("createdAt", System.currentTimeMillis())
            ),
            FindOneAndUpdateOptions().upsert(true)
        )
    }
}
