package com.transloom.repository.mongo

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.ne
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.StringWithTranslations
import com.transloom.domain.StringsPage
import com.transloom.domain.Translation
import com.transloom.domain.TranslationSummary
import com.transloom.repository.TranslationRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.UUID

class MongoTranslationRepository(db: MongoDatabase) : TranslationRepository {

    private val stringsCol = db.getCollection<Document>("strings")
    private val translationsCol = db.getCollection<Document>("translations")

    override suspend fun upsertString(projectId: String, key: String, sourceText: String): String {
        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        val filter = and(eq("projectId", projectId), eq("stringKey", key))
        val update = Updates.combine(
            Updates.setOnInsert("_id", newId),
            Updates.setOnInsert("projectId", projectId),
            Updates.setOnInsert("stringKey", key),
            Updates.setOnInsert("createdAt", now),
            Updates.set("sourceText", sourceText),
            Updates.set("updatedAt", now)
        )
        val doc = stringsCol.findOneAndUpdate(
            filter, update,
            FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        ) ?: error("upsertString returned null for projectId=$projectId key=$key")
        return doc.getString("_id")
    }

    override suspend fun upsertTranslation(
        stringId: String,
        targetLanguage: String,
        targetRegion: String?,
        translatedText: String,
        status: String,
        blockReason: String?
    ) {
        val now = System.currentTimeMillis()
        val filter = and(eq("stringId", stringId), eq("targetLanguage", targetLanguage))
        val setUpdates = mutableListOf(
            Updates.set("translatedText", translatedText),
            Updates.set("status", status),
            Updates.set("updatedAt", now)
        )
        if (blockReason != null) setUpdates += Updates.set("blockReason", blockReason)
        else setUpdates += Updates.unset("blockReason")

        val update = Updates.combine(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("stringId", stringId),
            Updates.setOnInsert("targetLanguage", targetLanguage),
            Updates.setOnInsert("targetRegion", targetRegion),
            Updates.setOnInsert("createdAt", now),
            *setUpdates.toTypedArray()
        )
        translationsCol.findOneAndUpdate(filter, update, FindOneAndUpdateOptions().upsert(true))
    }

    override suspend fun listStringsForProject(projectId: String, limit: Int, offset: Int): StringsPage {
        val total = stringsCol.countDocuments(eq("projectId", projectId)).toInt()

        val stringDocs = stringsCol
            .find(eq("projectId", projectId))
            .sort(Sorts.descending("createdAt"))
            .skip(offset)
            .limit(limit)
            .toList()

        if (stringDocs.isEmpty()) return StringsPage(emptyList(), total, limit, offset)

        val stringIds = stringDocs.map { it.getString("_id") }
        val translationsByStringId = translationsCol
            .find(`in`("stringId", stringIds))
            .toList()
            .groupBy { it.getString("stringId") }

        val result = stringDocs.map { strDoc ->
            val sid = strDoc.getString("_id")
            val summaries = translationsByStringId[sid]?.map { tDoc ->
                TranslationSummary(
                    language = tDoc.getString("targetLanguage"),
                    region = tDoc.getString("targetRegion"),
                    translatedText = tDoc.getString("translatedText") ?: "",
                    status = tDoc.getString("status") ?: "auto",
                    blockReason = tDoc.getString("blockReason")
                )
            } ?: emptyList()

            StringWithTranslations(
                id = sid,
                key = strDoc.getString("stringKey"),
                sourceText = strDoc.getString("sourceText"),
                translations = summaries
            )
        }
        return StringsPage(result, total, limit, offset)
    }

    override suspend fun getStringKeysAndTexts(projectId: String): Map<String, String> {
        return stringsCol.find(eq("projectId", projectId)).toList()
            .associate { it.getString("stringKey") to (it.getString("sourceText") ?: "") }
    }

    override suspend fun listPendingReviews(ownerId: String): List<Translation> {
        return translationsCol.aggregate<Document>(ownerPipeline(ownerId, extraMatch = `in`("status", listOf("review", "blocked"))))
            .toList().map { it.toTranslation() }
    }

    override suspend fun approve(translationId: String, editedText: String?): Boolean {
        val now = System.currentTimeMillis()
        val updates = mutableListOf(
            Updates.set("status", "approved"),  // "approved" = manually reviewed; follow-up PR will set to "auto"
            Updates.unset("blockReason"),
            Updates.set("updatedAt", now)
        )
        if (!editedText.isNullOrBlank()) updates += Updates.set("translatedText", editedText)
        val result = translationsCol.updateOne(eq("_id", translationId), Updates.combine(updates))
        return result.modifiedCount > 0
    }

    override suspend fun reject(translationId: String, reason: String): Boolean {
        val result = translationsCol.updateOne(
            eq("_id", translationId),
            Updates.combine(
                Updates.set("status", "blocked"),
                Updates.set("blockReason", reason),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
        return result.modifiedCount > 0
    }

    override suspend fun getTranslation(translationId: String): Translation? {
        val pipeline = listOf(
            Aggregates.match(eq("_id", translationId)),
            Aggregates.lookup("strings", "stringId", "_id", "strDoc"),
            Aggregates.unwind("\$strDoc"),
            Aggregates.lookup("projects", "strDoc.projectId", "_id", "projDoc"),
            Aggregates.unwind("\$projDoc")
        )
        return translationsCol.aggregate<Document>(pipeline).firstOrNull()?.toTranslation()
    }

    override suspend fun countByStatus(ownerId: String): Map<String, Int> {
        val result = mutableMapOf("auto" to 0, "review" to 0, "blocked" to 0)
        val pipeline = ownerPipeline(ownerId) + listOf(
            Aggregates.group("\$status", com.mongodb.client.model.Accumulators.sum("count", 1))
        )
        translationsCol.aggregate<Document>(pipeline).toList().forEach { doc ->
            val status = doc.getString("_id") ?: return@forEach
            val count = (doc["count"] as? Number)?.toInt() ?: 0
            // "approved" = manually reviewed but follow-up PR not yet created; counts as "auto" for stats
            val key = if (status == "approved") "auto" else status
            result[key] = (result[key] ?: 0) + count
        }
        return result
    }

    override suspend fun getApprovedForProject(projectId: String): List<Translation> {
        val pipeline = listOf(
            Aggregates.match(eq("status", "approved")),
            Aggregates.lookup("strings", "stringId", "_id", "strDoc"),
            Aggregates.unwind("\$strDoc"),
            Aggregates.match(eq("strDoc.projectId", projectId)),
            Aggregates.lookup("projects", "strDoc.projectId", "_id", "projDoc"),
            Aggregates.unwind("\$projDoc")
        )
        return translationsCol.aggregate<Document>(pipeline).toList().map { it.toTranslation() }
    }

    override suspend fun claimApproved(translationIds: List<String>): Int {
        if (translationIds.isEmpty()) return 0
        val result = translationsCol.updateMany(
            and(`in`("_id", translationIds), eq("status", "approved")),
            Updates.combine(
                Updates.set("status", "auto"),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
        return result.modifiedCount.toInt()
    }

    override suspend fun totalStringsTranslated(ownerId: String): Int {
        val pipeline = ownerPipeline(ownerId, extraMatch = ne("status", "blocked")) + listOf(
            Aggregates.count("total")
        )
        val doc = translationsCol.aggregate<Document>(pipeline).firstOrNull()
        return (doc?.get("total") as? Number)?.toInt() ?: 0
    }

    override suspend fun activeLanguageCount(ownerId: String): Int {
        val pipeline = ownerPipeline(ownerId) + listOf(
            Aggregates.group("\$targetLanguage")
        )
        return translationsCol.aggregate<Document>(pipeline).toList().size
    }

    override suspend fun revertToReview(translationId: String) {
        translationsCol.updateOne(
            eq("_id", translationId),
            Updates.combine(
                Updates.set("status", "review"),
                Updates.set("blockReason", "Follow-up PR creation failed — please re-approve"),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
    }

    private fun ownerPipeline(
        ownerId: String,
        extraMatch: org.bson.conversions.Bson? = null
    ): List<org.bson.conversions.Bson> {
        val stages = mutableListOf<org.bson.conversions.Bson>()
        if (extraMatch != null) stages += Aggregates.match(extraMatch)
        stages += Aggregates.lookup("strings", "stringId", "_id", "strDoc")
        stages += Aggregates.unwind("\$strDoc")
        stages += Aggregates.lookup("projects", "strDoc.projectId", "_id", "projDoc")
        stages += Aggregates.unwind("\$projDoc")
        stages += Aggregates.match(eq("projDoc.ownerId", ownerId))
        stages += Aggregates.sort(Sorts.descending("updatedAt"))
        return stages
    }

    private fun Document.toTranslation(): Translation {
        val strDoc = get("strDoc", Document::class.java)
        val projDoc = get("projDoc", Document::class.java)
        return Translation(
            id = getString("_id"),
            stringId = getString("stringId"),
            stringKey = strDoc?.getString("stringKey") ?: "",
            sourceText = strDoc?.getString("sourceText") ?: "",
            targetLanguage = getString("targetLanguage"),
            targetRegion = getString("targetRegion"),
            translatedText = getString("translatedText") ?: "",
            status = getString("status") ?: "auto",
            blockReason = getString("blockReason"),
            projectId = strDoc?.getString("projectId") ?: "",
            projectName = projDoc?.getString("name") ?: ""
        )
    }
}
