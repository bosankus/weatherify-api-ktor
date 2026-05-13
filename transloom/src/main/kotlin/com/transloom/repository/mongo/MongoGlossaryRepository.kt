package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.GlossaryEntry
import com.transloom.repository.GlossaryRepository
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.UUID

class MongoGlossaryRepository(db: MongoDatabase) : GlossaryRepository {

    private val collection = db.getCollection<Document>("glossary")

    override suspend fun upsert(projectId: String, languageCode: String, sourceTerm: String, targetTerm: String) {
        val now = System.currentTimeMillis()
        val filter = and(eq("projectId", projectId), eq("languageCode", languageCode), eq("sourceTerm", sourceTerm))
        val update = Updates.combine(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("projectId", projectId),
            Updates.setOnInsert("languageCode", languageCode),
            Updates.setOnInsert("sourceTerm", sourceTerm),
            Updates.setOnInsert("createdAt", now),
            Updates.set("targetTerm", targetTerm),
            Updates.set("isActive", true),
            Updates.set("updatedAt", now)
        )
        collection.findOneAndUpdate(filter, update, FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER))
    }

    override suspend fun listWithIds(projectId: String): List<GlossaryEntry> {
        return collection
            .find(and(eq("projectId", projectId), eq("isActive", true)))
            .sort(Sorts.ascending("languageCode"))
            .toList()
            .map { doc ->
                GlossaryEntry(
                    id = doc.getString("_id"),
                    projectId = doc.getString("projectId"),
                    languageCode = doc.getString("languageCode"),
                    sourceTerm = doc.getString("sourceTerm"),
                    targetTerm = doc.getString("targetTerm"),
                    isActive = doc.getBoolean("isActive", true)
                )
            }
    }

    override suspend fun deactivate(entryId: String, projectId: String): Boolean {
        val result = collection.updateOne(
            and(eq("_id", entryId), eq("projectId", projectId)),
            Updates.combine(Updates.set("isActive", false), Updates.set("updatedAt", System.currentTimeMillis()))
        )
        return result.modifiedCount > 0
    }
}
