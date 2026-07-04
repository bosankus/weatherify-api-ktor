package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.domain.FigmaCandidateStatus
import com.syncling.domain.FigmaFramePreview
import com.syncling.domain.FigmaNodeBinding
import com.syncling.domain.FigmaProjectSettings
import com.syncling.domain.FigmaStringCandidate
import com.syncling.repository.FigmaCandidateRepository
import com.syncling.repository.FigmaNodeBindingRepository
import com.syncling.repository.FigmaPreviewRepository
import com.syncling.repository.FigmaSettingsRepository
import org.bson.types.Binary
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bson.Document
import org.bson.conversions.Bson
import java.util.UUID

class MongoFigmaCandidateRepository(db: MongoDatabase) : FigmaCandidateRepository {

    private val col = db.getCollection<Document>("figma_candidates")

    override suspend fun upsertPending(candidate: FigmaStringCandidate): FigmaStringCandidate {
        val now = Clock.System.now()
        val pendingFilter = and(
            eq("projectId", candidate.projectId),
            eq("figmaFileKey", candidate.figmaFileKey),
            eq("figmaNodeId", candidate.figmaNodeId),
            eq("status", FigmaCandidateStatus.PENDING.name),
        )
        val existing = col.find(pendingFilter).firstOrNull()
        if (existing != null) {
            // Refresh the pending row but keep the dev's key edit and the original creation time.
            col.updateOne(
                pendingFilter,
                Updates.combine(
                    Updates.set("nodeName", candidate.nodeName),
                    Updates.set("pageName", candidate.pageName),
                    Updates.set("frameName", candidate.frameName),
                    Updates.set("figmaFrameId", candidate.figmaFrameId),
                    Updates.set("sourceText", candidate.sourceText),
                    Updates.set("suggestedKey", candidate.suggestedKey),
                    Updates.set("duplicateOfKey", candidate.duplicateOfKey),
                    Updates.set("similarToKey", candidate.similarToKey),
                    Updates.set("similarityScore", candidate.similarityScore?.toDouble()),
                    Updates.set("boundKey", candidate.boundKey),
                    Updates.set("submittedByUserId", candidate.submittedByUserId),
                    Updates.set("updatedAt", now.toEpochMilliseconds()),
                ),
            )
            return col.find(pendingFilter).firstOrNull()?.toCandidate()
                ?: candidate.copy(id = existing.getString("_id"), updatedAt = now)
        }
        val stored = candidate.copy(
            id = candidate.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = now, updatedAt = now,
        )
        col.insertOne(stored.toDocument())
        return stored
    }

    override suspend fun listForProject(
        projectId: String,
        status: FigmaCandidateStatus?,
        limit: Int,
        offset: Int,
    ): List<FigmaStringCandidate> =
        col.find(projectFilter(projectId, status))
            .sort(Sorts.descending("updatedAt"))
            .skip(offset).limit(limit)
            .toList().map { it.toCandidate() }

    override suspend fun countForProject(projectId: String, status: FigmaCandidateStatus?): Int =
        col.countDocuments(projectFilter(projectId, status)).toInt()

    override suspend fun findById(id: String): FigmaStringCandidate? =
        col.find(eq("_id", id)).firstOrNull()?.toCandidate()

    override suspend fun findByIds(ids: List<String>): List<FigmaStringCandidate> =
        if (ids.isEmpty()) emptyList()
        else col.find(`in`("_id", ids)).toList().map { it.toCandidate() }

    override suspend fun updateFinalKey(id: String, projectId: String, finalKey: String): Boolean =
        col.updateOne(
            and(eq("_id", id), eq("projectId", projectId), eq("status", FigmaCandidateStatus.PENDING.name)),
            Updates.combine(
                Updates.set("finalKey", finalKey),
                Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds()),
            ),
        ).modifiedCount > 0

    override suspend fun markPrOpened(ids: List<String>, prUrl: String): Int {
        if (ids.isEmpty()) return 0
        return col.updateMany(
            and(`in`("_id", ids), eq("status", FigmaCandidateStatus.PENDING.name)),
            Updates.combine(
                Updates.set("status", FigmaCandidateStatus.PR_OPEN.name),
                Updates.set("prUrl", prUrl),
                Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds()),
            ),
        ).modifiedCount.toInt()
    }

    override suspend fun markRejected(ids: List<String>, projectId: String): Int {
        if (ids.isEmpty()) return 0
        return col.updateMany(
            and(`in`("_id", ids), eq("projectId", projectId), eq("status", FigmaCandidateStatus.PENDING.name)),
            Updates.combine(
                Updates.set("status", FigmaCandidateStatus.REJECTED.name),
                Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds()),
            ),
        ).modifiedCount.toInt()
    }

    override suspend fun findRejectedTexts(
        projectId: String,
        fileKey: String,
        nodeIds: List<String>,
    ): Map<String, Set<String>> {
        if (nodeIds.isEmpty()) return emptyMap()
        return col.find(
            and(
                eq("projectId", projectId),
                eq("figmaFileKey", fileKey),
                `in`("figmaNodeId", nodeIds),
                eq("status", FigmaCandidateStatus.REJECTED.name),
            ),
        ).toList()
            .groupBy({ it.getString("figmaNodeId") }, { it.getString("sourceText") })
            .mapValues { (_, texts) -> texts.toSet() }
    }

    private fun projectFilter(projectId: String, status: FigmaCandidateStatus?): Bson =
        if (status == null) eq("projectId", projectId)
        else and(eq("projectId", projectId), eq("status", status.name))

    private fun FigmaStringCandidate.toDocument(): Document =
        Document("_id", id)
            .append("projectId", projectId)
            .append("figmaFileKey", figmaFileKey)
            .append("figmaNodeId", figmaNodeId)
            .append("nodeName", nodeName)
            .append("pageName", pageName)
            .append("frameName", frameName)
            .append("figmaFrameId", figmaFrameId)
            .append("sourceText", sourceText)
            .append("suggestedKey", suggestedKey)
            .append("finalKey", finalKey)
            .append("status", status.name)
            .append("duplicateOfKey", duplicateOfKey)
            .append("similarToKey", similarToKey)
            .append("similarityScore", similarityScore?.toDouble())
            .append("boundKey", boundKey)
            .append("submittedByUserId", submittedByUserId)
            .append("prUrl", prUrl)
            .append("createdAt", createdAt.toEpochMilliseconds())
            .append("updatedAt", updatedAt.toEpochMilliseconds())

    private fun Document.toCandidate(): FigmaStringCandidate =
        FigmaStringCandidate(
            id = getString("_id"),
            projectId = getString("projectId"),
            figmaFileKey = getString("figmaFileKey"),
            figmaNodeId = getString("figmaNodeId"),
            nodeName = getString("nodeName") ?: "",
            pageName = getString("pageName"),
            frameName = getString("frameName"),
            figmaFrameId = getString("figmaFrameId"),
            sourceText = getString("sourceText"),
            suggestedKey = getString("suggestedKey"),
            finalKey = getString("finalKey"),
            status = getString("status")?.let { s -> FigmaCandidateStatus.entries.firstOrNull { it.name == s } }
                ?: FigmaCandidateStatus.PENDING,
            duplicateOfKey = getString("duplicateOfKey"),
            similarToKey = getString("similarToKey"),
            similarityScore = getDouble("similarityScore")?.toFloat(),
            boundKey = getString("boundKey"),
            submittedByUserId = getString("submittedByUserId"),
            prUrl = getString("prUrl"),
            createdAt = Instant.fromEpochMilliseconds(getLong("createdAt")),
            updatedAt = Instant.fromEpochMilliseconds(getLong("updatedAt")),
        )
}

class MongoFigmaNodeBindingRepository(db: MongoDatabase) : FigmaNodeBindingRepository {

    private val col = db.getCollection<Document>("figma_node_bindings")

    override suspend fun findForNodes(
        projectId: String,
        fileKey: String,
        nodeIds: List<String>,
    ): List<FigmaNodeBinding> =
        if (nodeIds.isEmpty()) emptyList()
        else col.find(
            and(eq("projectId", projectId), eq("figmaFileKey", fileKey), `in`("figmaNodeId", nodeIds)),
        ).toList().map { it.toBinding() }

    override suspend fun upsertAll(bindings: List<FigmaNodeBinding>) {
        for (b in bindings) {
            col.replaceOne(
                and(eq("projectId", b.projectId), eq("figmaFileKey", b.figmaFileKey), eq("figmaNodeId", b.figmaNodeId)),
                Document("projectId", b.projectId)
                    .append("figmaFileKey", b.figmaFileKey)
                    .append("figmaNodeId", b.figmaNodeId)
                    .append("stringKey", b.stringKey)
                    .append("lastText", b.lastText)
                    .append("figmaFrameId", b.figmaFrameId)
                    .append("updatedAt", b.updatedAt.toEpochMilliseconds()),
                ReplaceOptions().upsert(true),
            )
        }
    }

    override suspend fun listForProject(projectId: String, limit: Int): List<FigmaNodeBinding> =
        col.find(eq("projectId", projectId)).limit(limit).toList().map { it.toBinding() }

    private fun Document.toBinding(): FigmaNodeBinding =
        FigmaNodeBinding(
            projectId = getString("projectId"),
            figmaFileKey = getString("figmaFileKey"),
            figmaNodeId = getString("figmaNodeId"),
            stringKey = getString("stringKey"),
            lastText = getString("lastText") ?: "",
            figmaFrameId = getString("figmaFrameId"),
            updatedAt = Instant.fromEpochMilliseconds(getLong("updatedAt")),
        )
}

class MongoFigmaSettingsRepository(db: MongoDatabase) : FigmaSettingsRepository {

    private val col = db.getCollection<Document>("figma_settings")

    override suspend fun get(projectId: String): FigmaProjectSettings =
        col.find(eq("projectId", projectId)).firstOrNull()?.toSettings()
            ?: FigmaProjectSettings(projectId = projectId, updatedAt = Clock.System.now())

    override suspend fun setAutoApprove(projectId: String, autoApprove: Boolean): FigmaProjectSettings {
        val now = Clock.System.now()
        col.replaceOne(
            eq("projectId", projectId),
            Document("projectId", projectId)
                .append("autoApprove", autoApprove)
                .append("updatedAt", now.toEpochMilliseconds()),
            ReplaceOptions().upsert(true),
        )
        return FigmaProjectSettings(projectId = projectId, autoApprove = autoApprove, updatedAt = now)
    }

    private fun Document.toSettings(): FigmaProjectSettings =
        FigmaProjectSettings(
            projectId = getString("projectId"),
            autoApprove = getBoolean("autoApprove", false),
            updatedAt = Instant.fromEpochMilliseconds(getLong("updatedAt")),
        )
}

class MongoFigmaPreviewRepository(db: MongoDatabase) : FigmaPreviewRepository {

    private val col = db.getCollection<Document>("figma_previews")

    override suspend fun upsert(preview: FigmaFramePreview) {
        col.replaceOne(
            and(
                eq("projectId", preview.projectId),
                eq("figmaFileKey", preview.figmaFileKey),
                eq("figmaFrameId", preview.figmaFrameId),
            ),
            Document("projectId", preview.projectId)
                .append("figmaFileKey", preview.figmaFileKey)
                .append("figmaFrameId", preview.figmaFrameId)
                .append("png", preview.png)
                .append("updatedAt", preview.updatedAt.toEpochMilliseconds()),
            ReplaceOptions().upsert(true),
        )
    }

    override suspend fun find(projectId: String, fileKey: String, frameId: String): FigmaFramePreview? =
        col.find(
            and(eq("projectId", projectId), eq("figmaFileKey", fileKey), eq("figmaFrameId", frameId)),
        ).firstOrNull()?.let { doc ->
            val png = when (val raw = doc.get("png")) {
                is Binary -> raw.data
                is ByteArray -> raw
                else -> return@let null
            }
            FigmaFramePreview(
                projectId = doc.getString("projectId"),
                figmaFileKey = doc.getString("figmaFileKey"),
                figmaFrameId = doc.getString("figmaFrameId"),
                png = png,
                updatedAt = Instant.fromEpochMilliseconds(doc.getLong("updatedAt")),
            )
        }
}
