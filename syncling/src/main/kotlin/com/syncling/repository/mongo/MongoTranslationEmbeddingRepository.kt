package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.exists
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.repository.EmbeddingRow
import com.syncling.repository.TranslationEmbeddingRepository
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.security.MessageDigest
import java.util.UUID

class MongoTranslationEmbeddingRepository(db: MongoDatabase) : TranslationEmbeddingRepository {

    private val col = db.getCollection<Document>("translation_embeddings")

    override suspend fun upsert(
        projectId: String,
        sourceText: String,
        embedding: FloatArray,
        targetLanguage: String,
        translatedText: String
    ) {
        val key = hashKey(projectId, sourceText)
        val now = System.currentTimeMillis()
        val update = Updates.combine(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("hashKey", key),
            Updates.setOnInsert("projectId", projectId),
            Updates.setOnInsert("sourceText", sourceText),
            Updates.setOnInsert("embedding", embedding.toList()),
            Updates.setOnInsert("createdAt", now),
            Updates.set("translations.$targetLanguage", translatedText),
            Updates.set("updatedAt", now)
        )
        col.findOneAndUpdate(
            eq("hashKey", key), update,
            FindOneAndUpdateOptions().upsert(true)
        )
    }

    override suspend fun listForLanguage(projectId: String, targetLanguage: String): List<EmbeddingRow> =
        col.find(
            and(
                eq("projectId", projectId),
                exists("translations.$targetLanguage")
            )
        ).toList().mapNotNull { it.toRow(targetLanguage) }

    @Suppress("UNCHECKED_CAST")
    private fun Document.toRow(targetLanguage: String): EmbeddingRow? {
        val src = getString("sourceText") ?: return null
        val raw = (get("embedding") as? List<Number>) ?: return null
        val translations = (get("translations") as? Document)
            ?.entries?.associate { (k, v) -> k to v.toString() } ?: return null
        val tgt = translations[targetLanguage] ?: return null
        return EmbeddingRow(
            sourceText = src,
            embedding = FloatArray(raw.size) { raw[it].toFloat() },
            translations = mapOf(targetLanguage to tgt)
        )
    }

    private fun hashKey(projectId: String, sourceText: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$projectId|$sourceText".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
