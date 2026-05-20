package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.CdnPublishLog
import com.transloom.repository.CdnPublishRepository
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.util.UUID

class MongoCdnPublishRepository(db: MongoDatabase) : CdnPublishRepository {

    private val col = db.getCollection<Document>("cdn_publish_log")

    override suspend fun log(
        projectId: String,
        bundleVersion: String,
        locales: List<String>,
        status: String
    ): CdnPublishLog {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        // Upsert by (projectId, bundleVersion) so concurrent publishes of identical content are idempotent.
        val filter = com.mongodb.client.model.Filters.and(
            eq("projectId", projectId),
            eq("bundleVersion", bundleVersion)
        )
        val doc = col.findOneAndUpdate(
            filter,
            Updates.combine(
                Updates.setOnInsert("_id", id),
                Updates.setOnInsert("projectId", projectId),
                Updates.setOnInsert("bundleVersion", bundleVersion),
                Updates.setOnInsert("publishedAt", now),
                Updates.set("locales", locales),
                Updates.set("status", status),
                Updates.set("updatedAt", now)
            ),
            FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        ) ?: error("cdn_publish_log upsert returned null")
        return doc.toCdnPublishLog()
    }

    override suspend fun lastPublish(projectId: String): CdnPublishLog? {
        return col.find(eq("projectId", projectId))
            .sort(Sorts.descending("publishedAt"))
            .firstOrNull()
            ?.toCdnPublishLog()
    }

    private fun Document.toCdnPublishLog() = CdnPublishLog(
        id = getString("_id"),
        projectId = getString("projectId"),
        bundleVersion = getString("bundleVersion"),
        publishedAt = getLong("publishedAt") ?: 0L,
        locales = getList("locales", String::class.java) ?: emptyList(),
        status = getString("status") ?: "unknown"
    )
}
