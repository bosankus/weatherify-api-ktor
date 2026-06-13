package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.domain.QuotaBlockedRun
import com.syncling.repository.QuotaBlockedRunRepository
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.UUID

class MongoQuotaBlockedRunRepository(db: MongoDatabase) : QuotaBlockedRunRepository {

    private val collection = db.getCollection<Document>("quota_blocked_runs")

    override suspend fun upsert(run: QuotaBlockedRun) {
        val updates = mutableListOf(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("blockedAt", run.blockedAt),
            Updates.set("ownerId", run.ownerId),
            Updates.set("repo", run.repo),
            Updates.set("branch", run.branch),
            Updates.set("commitHash", run.commitHash),
            // BSON Date (not epoch Long) so the TTL index can expire abandoned records.
            Updates.set("updatedAt", java.util.Date())
        )
        if (run.stringsPending > 0) {
            updates += Updates.set("stringsPending", run.stringsPending)
            updates += Updates.set("languagesPending", run.languagesPending)
            updates += Updates.set("originalRunId", run.originalRunId)
        }
        collection.updateOne(eq("projectId", run.projectId), Updates.combine(updates), UpdateOptions().upsert(true))
    }

    override suspend fun listForOwner(ownerId: String): List<QuotaBlockedRun> =
        collection.find(eq("ownerId", ownerId)).toList().map { it.toBlockedRun() }

    override suspend fun delete(projectId: String) {
        collection.deleteOne(eq("projectId", projectId))
    }

    private fun Document.toBlockedRun() = QuotaBlockedRun(
        projectId = getString("projectId"),
        ownerId = getString("ownerId"),
        repo = getString("repo"),
        branch = getString("branch"),
        commitHash = getString("commitHash"),
        originalRunId = getString("originalRunId"),
        stringsPending = (get("stringsPending") as? Number)?.toInt() ?: 0,
        languagesPending = (get("languagesPending") as? Number)?.toInt() ?: 0,
        blockedAt = (get("blockedAt") as? Number)?.toLong() ?: 0L
    )
}
