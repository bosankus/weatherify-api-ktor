package com.transloom.repository.mongo

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.exists
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.ne
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.WriteModel
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.StringWithTranslations
import com.transloom.domain.StringsPage
import com.transloom.domain.Translation
import com.transloom.domain.TranslationHistoryEntry
import com.transloom.domain.TranslationSummary
import com.transloom.repository.TranslationRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.slf4j.LoggerFactory
import java.util.UUID

class MongoTranslationRepository(db: MongoDatabase) : TranslationRepository {

    private val log = LoggerFactory.getLogger(MongoTranslationRepository::class.java)

    private val stringsCol = db.getCollection<Document>("strings")
    private val translationsCol = db.getCollection<Document>("translations")
    private val historyCol = db.getCollection<Document>("translation_history")
    private val projectsCol = db.getCollection<Document>("projects")

    override suspend fun upsertString(projectId: String, key: String, sourceText: String): String {
        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        val filter = and(eq("projectId", projectId), eq("stringKey", key))

        // Detect sourceText change so we can fan out to denormalized translations
        val existing = stringsCol.find(filter).firstOrNull()
        val prevSourceText = existing?.getString("sourceText")

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
        val stringId = doc.getString("_id")

        if (prevSourceText != null && prevSourceText != sourceText) {
            translationsCol.updateMany(eq("stringId", stringId), Updates.set("sourceText", sourceText))
        }
        return stringId
    }

    override suspend fun upsertTranslation(
        stringId: String,
        projectId: String,
        ownerId: String,
        stringKey: String,
        sourceText: String,
        projectName: String,
        targetLanguage: String,
        targetRegion: String?,
        translatedText: String,
        status: String,
        blockReason: String?,
        pipelineRunId: String?,
        commitShort: String?
    ) {
        val now = System.currentTimeMillis()
        val filter = and(eq("stringId", stringId), eq("targetLanguage", targetLanguage))

        // Respect lock: if an existing translation is locked, the pipeline must not overwrite it.
        val existingDoc = translationsCol.find(filter).firstOrNull()
        if (existingDoc?.get("lockedAt") != null) return

        val setUpdates = mutableListOf(
            Updates.set("translatedText", translatedText),
            Updates.set("status", status),
            Updates.set("updatedAt", now),
            // Denormalized fields — kept fresh on every write
            Updates.set("projectId", projectId),
            Updates.set("ownerId", ownerId),
            Updates.set("stringKey", stringKey),
            Updates.set("sourceText", sourceText),
            Updates.set("projectName", projectName)
        )
        if (blockReason != null) setUpdates += Updates.set("blockReason", blockReason)
        else setUpdates += Updates.unset("blockReason")
        if (pipelineRunId != null) setUpdates += Updates.set("pipelineRunId", pipelineRunId)
        if (commitShort != null) setUpdates += Updates.set("commitShort", commitShort)

        val update = Updates.combine(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("stringId", stringId),
            Updates.setOnInsert("targetLanguage", targetLanguage),
            Updates.setOnInsert("targetRegion", targetRegion),
            Updates.setOnInsert("createdAt", now),
            *setUpdates.toTypedArray()
        )
        val before = translationsCol.findOneAndUpdate(
            filter, update,
            FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.BEFORE)
        )
        val prevText = before?.getString("translatedText")?.takeIf { it.isNotBlank() && it != translatedText }
        if (prevText != null) {
            // Store diff for review portal side-by-side view
            translationsCol.updateOne(filter, Updates.set("previousTranslatedText", prevText))

            // Write to immutable history log — query: GET /projects/{id}/strings/{key}/history
            val translationId = before.getString("_id") ?: return
            historyCol.insertOne(Document().apply {
                put("_id", UUID.randomUUID().toString())
                put("translationId", translationId)
                put("stringKey", stringKey)
                put("projectId", projectId)
                put("targetLanguage", targetLanguage)
                put("previousText", prevText)
                put("newText", translatedText)
                put("changedAt", now)
                put("changedBy", if (pipelineRunId != null) "pipeline" else "manual")
                if (pipelineRunId != null) put("pipelineRunId", pipelineRunId)
            })
        }
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

    override suspend fun listPendingReviews(
        ownerId: String,
        limit: Int,
        offset: Int,
        language: String?,
        statusFilter: String?
    ): List<Translation> {
        val filter = and(eq("ownerId", ownerId), reviewMatch(language, statusFilter))
        return translationsCol.find(filter)
            .sort(Sorts.descending("updatedAt"))
            .skip(offset)
            .limit(limit)
            .toList()
            .map { it.toTranslation() }
    }

    override suspend fun countPendingReviews(ownerId: String, language: String?, statusFilter: String?): Int {
        val filter = and(eq("ownerId", ownerId), reviewMatch(language, statusFilter))
        return translationsCol.countDocuments(filter).toInt()
    }

    private fun reviewMatch(language: String?, statusFilter: String?): org.bson.conversions.Bson {
        val statusBson = if (statusFilter != null) eq("status", statusFilter)
                         else `in`("status", listOf("review", "blocked"))
        return if (language != null) and(statusBson, eq("targetLanguage", language)) else statusBson
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

    override suspend fun hotfix(translationId: String, newText: String): Boolean {
        if (newText.isBlank()) return false
        val now = System.currentTimeMillis()
        val result = translationsCol.updateOne(
            eq("_id", translationId),
            Updates.combine(
                Updates.set("translatedText", newText),
                Updates.set("status", "auto"),
                Updates.unset("blockReason"),
                Updates.set("updatedAt", now)
            )
        )
        return result.modifiedCount > 0
    }

    override suspend fun approveMany(translationIds: List<String>): Int {
        if (translationIds.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val result = translationsCol.updateMany(
            and(`in`("_id", translationIds), eq("status", "review")),
            Updates.combine(
                Updates.set("status", "approved"),
                Updates.unset("blockReason"),
                Updates.set("updatedAt", now)
            )
        )
        return result.modifiedCount.toInt()
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
        return translationsCol.find(eq("_id", translationId)).firstOrNull()?.toTranslation()
    }

    override suspend fun countByStatus(ownerId: String): Map<String, Int> {
        val result = mutableMapOf("auto" to 0, "review" to 0, "blocked" to 0)
        val pipeline = listOf(
            Aggregates.match(eq("ownerId", ownerId)),
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
        return translationsCol.find(and(eq("projectId", projectId), eq("status", "approved")))
            .toList()
            .map { it.toTranslation() }
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
        return translationsCol.countDocuments(and(eq("ownerId", ownerId), ne("status", "blocked"))).toInt()
    }

    override suspend fun activeLanguageCount(ownerId: String): Int {
        val pipeline = listOf(
            Aggregates.match(eq("ownerId", ownerId)),
            Aggregates.group("\$targetLanguage")
        )
        return translationsCol.aggregate<Document>(pipeline).toList().size
    }

    override suspend fun getPublishableTranslations(projectId: String): List<Translation> {
        return translationsCol.find(
            and(eq("projectId", projectId), `in`("status", listOf("auto", "approved")))
        ).toList().map { it.toTranslation() }
    }

    override suspend fun listByIds(ids: List<String>): List<Translation> {
        if (ids.isEmpty()) return emptyList()
        return translationsCol.find(`in`("_id", ids)).toList().map { it.toTranslation() }
    }

    override suspend fun lock(translationId: String, lockedBy: String): Boolean {
        val result = translationsCol.updateOne(
            and(eq("_id", translationId), eq("lockedAt", null)),
            Updates.combine(
                Updates.set("lockedAt", System.currentTimeMillis()),
                Updates.set("lockedBy", lockedBy),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
        return result.modifiedCount > 0
    }

    override suspend fun unlock(translationId: String): Boolean {
        val result = translationsCol.updateOne(
            eq("_id", translationId),
            Updates.combine(
                Updates.unset("lockedAt"),
                Updates.unset("lockedBy"),
                Updates.set("updatedAt", System.currentTimeMillis())
            )
        )
        return result.modifiedCount > 0
    }

    override suspend fun listHistory(projectId: String, stringKey: String, limit: Int): List<TranslationHistoryEntry> =
        historyCol.find(and(eq("projectId", projectId), eq("stringKey", stringKey)))
            .sort(Sorts.descending("changedAt"))
            .limit(limit)
            .toList()
            .map { doc ->
                TranslationHistoryEntry(
                    id = doc.getString("_id"),
                    translationId = doc.getString("translationId") ?: "",
                    stringKey = doc.getString("stringKey") ?: "",
                    projectId = doc.getString("projectId") ?: "",
                    targetLanguage = doc.getString("targetLanguage") ?: "",
                    previousText = doc.getString("previousText") ?: "",
                    newText = doc.getString("newText") ?: "",
                    changedAt = (doc["changedAt"] as? Number)?.toLong() ?: 0L,
                    changedBy = doc.getString("changedBy") ?: "pipeline",
                    pipelineRunId = doc.getString("pipelineRunId")
                )
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

    // ── Backfill ──────────────────────────────────────────────────────────────
    // Populates denormalized fields on translations docs created before the
    // denormalization landed. Idempotent: only touches docs missing `projectName`
    // (the sentinel field, written on every fresh upsert).
    override suspend fun backfillDenormalizedFields(): Int {
        val missing = translationsCol.find(or(exists("projectName", false), eq("projectName", null)))
            .toList()
        if (missing.isEmpty()) return 0

        // Build lookup tables in two batched queries — much cheaper than per-doc joins
        val stringIds = missing.mapNotNull { it.getString("stringId") }.distinct()
        if (stringIds.isEmpty()) return 0
        val stringDocs = stringsCol.find(`in`("_id", stringIds)).toList()
        val stringById = stringDocs.associateBy { it.getString("_id") }

        val projectIds = stringDocs.mapNotNull { it.getString("projectId") }.distinct()
        val projectDocs = if (projectIds.isEmpty()) emptyList()
                          else projectsCol.find(`in`("_id", projectIds)).toList()
        val projectById = projectDocs.associateBy { it.getString("_id") }

        val ops: List<WriteModel<Document>> = missing.mapNotNull { tDoc ->
            val sid = tDoc.getString("stringId") ?: return@mapNotNull null
            val s = stringById[sid] ?: return@mapNotNull null
            val pid = s.getString("projectId") ?: return@mapNotNull null
            val p = projectById[pid]
            UpdateOneModel<Document>(
                eq("_id", tDoc.getString("_id")),
                Updates.combine(
                    Updates.set("projectId", pid),
                    Updates.set("ownerId", p?.getString("ownerId") ?: ""),
                    Updates.set("stringKey", s.getString("stringKey") ?: ""),
                    Updates.set("sourceText", s.getString("sourceText") ?: ""),
                    Updates.set("projectName", p?.getString("name") ?: "")
                )
            )
        }
        if (ops.isEmpty()) return 0
        val result = translationsCol.bulkWrite(ops)
        log.info("Backfilled denormalized fields on {} translations", result.modifiedCount)
        return result.modifiedCount
    }

    private fun Document.toTranslation(): Translation = Translation(
        id = getString("_id"),
        stringId = getString("stringId"),
        stringKey = getString("stringKey") ?: "",
        sourceText = getString("sourceText") ?: "",
        targetLanguage = getString("targetLanguage"),
        targetRegion = getString("targetRegion"),
        translatedText = getString("translatedText") ?: "",
        status = getString("status") ?: "auto",
        blockReason = getString("blockReason"),
        projectId = getString("projectId") ?: "",
        projectName = getString("projectName") ?: "",
        pipelineRunId = getString("pipelineRunId"),
        commitShort = getString("commitShort"),
        previousTranslatedText = getString("previousTranslatedText"),
        lockedAt = (get("lockedAt") as? Number)?.toLong(),
        lockedBy = getString("lockedBy")
    )
}
