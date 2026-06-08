package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gte
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mongodb.client.model.Projections
import com.syncling.domain.PipelineRunSummary
import com.syncling.domain.PlatformPipelineStats
import com.syncling.repository.PipelineRunRepository
import kotlinx.coroutines.flow.toList
import org.bson.Document

class MongoPipelineRunRepository(db: MongoDatabase) : PipelineRunRepository {

    private val col = db.getCollection<Document>("pipeline_runs")

    override suspend fun persist(summary: PipelineRunSummary) {
        val doc = Document().apply {
            put("_id", summary.runId)
            put("projectId", summary.projectId)
            put("ownerId", summary.ownerId)
            put("triggeredByUserId", summary.triggeredByUserId)
            put("triggeredByLabel", summary.triggeredByLabel)
            put("repo", summary.repo)
            put("branch", summary.branch)
            put("commitShort", summary.commitShort)
            put("startedAt", summary.startedAt)
            put("finishedAt", summary.finishedAt)
            put("durationMs", summary.durationMs)
            put("status", summary.status)
            put("stringsTranslated", summary.stringsTranslated)
            put("stringsPerLocale", Document(summary.stringsPerLocale))
            put("error", summary.error)
            put("cacheHits", summary.cacheHits)
            put("tokensIn", summary.tokensIn)
            put("tokensOut", summary.tokensOut)
            put("estimatedCostUsd", summary.estimatedCostUsd)
        }
        col.findOneAndReplace(eq("_id", summary.runId), doc, FindOneAndReplaceOptions().upsert(true))
    }

    override suspend fun listForOwner(ownerId: String, sinceMillis: Long, limit: Int): List<PipelineRunSummary> =
        col.find(and(eq("ownerId", ownerId), gte("startedAt", sinceMillis)))
            .sort(Sorts.descending("startedAt"))
            .limit(limit)
            .toList()
            .map { it.toSummary() }

    override suspend fun listForProject(projectId: String, sinceMillis: Long, limit: Int): List<PipelineRunSummary> =
        col.find(and(eq("projectId", projectId), gte("startedAt", sinceMillis)))
            .sort(Sorts.descending("startedAt"))
            .limit(limit)
            .toList()
            .map { it.toSummary() }

    override suspend fun listForMember(memberUserId: String, sinceMillis: Long, limit: Int): List<PipelineRunSummary> =
        col.find(and(eq("triggeredByUserId", memberUserId), gte("startedAt", sinceMillis)))
            .sort(Sorts.descending("startedAt"))
            .limit(limit)
            .toList()
            .map { it.toSummary() }

    override suspend fun platformStats(sinceMillis: Long): PlatformPipelineStats {
        // Two cheap queries: counts by status, then bounded durations for percentiles.
        // Percentiles are computed in-memory off a bounded list to keep this fast
        // and avoid requiring MongoDB 7.0+ ($percentile operator).
        val window = System.currentTimeMillis() - sinceMillis

        val statusDocs = col.find(gte("startedAt", sinceMillis))
            .projection(Projections.include("status", "stringsTranslated"))
            .toList()

        var succeeded = 0
        var failed = 0
        var totalStrings = 0L
        for (d in statusDocs) {
            when (d.getString("status")) {
                "succeeded" -> succeeded++
                "failed", "errored", "error" -> failed++
            }
            totalStrings += (d.get("stringsTranslated") as? Number)?.toLong() ?: 0L
        }

        // Bounded sample for percentiles. 5000 is plenty for any realistic window.
        val durations = col.find(
            and(
                gte("startedAt", sinceMillis),
                eq("status", "succeeded"),
            )
        )
            .projection(Projections.include("durationMs"))
            .limit(5000)
            .toList()
            .mapNotNull { (it.get("durationMs") as? Number)?.toLong() }
            .filter { it > 0 }
            .sorted()

        fun pct(p: Double): Long? {
            if (durations.isEmpty()) return null
            val idx = ((p / 100.0) * (durations.size - 1)).toInt().coerceIn(0, durations.size - 1)
            return durations[idx]
        }

        return PlatformPipelineStats(
            windowMillis = window,
            totalRuns = statusDocs.size,
            succeededRuns = succeeded,
            failedRuns = failed,
            durationP50Ms = pct(50.0),
            durationP95Ms = pct(95.0),
            durationP99Ms = pct(99.0),
            totalStringsTranslated = totalStrings,
        )
    }

    override suspend fun earliestStartedAtForOwner(ownerId: String): Long? {
        val doc = col.find(eq("ownerId", ownerId))
            .sort(Sorts.ascending("startedAt"))
            .limit(1)
            .toList()
            .firstOrNull() ?: return null
        return (doc.get("startedAt") as? Number)?.toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Document.toSummary(): PipelineRunSummary {
        val perLocaleDoc = get("stringsPerLocale") as? Document
        val perLocale: Map<String, Int> = perLocaleDoc?.entries
            ?.associate { it.key to ((it.value as? Number)?.toInt() ?: 0) } ?: emptyMap()
        return PipelineRunSummary(
            runId = getString("_id"),
            projectId = getString("projectId") ?: "",
            ownerId = getString("ownerId") ?: "",
            triggeredByUserId = getString("triggeredByUserId"),
            triggeredByLabel = getString("triggeredByLabel") ?: "external",
            repo = getString("repo") ?: "",
            branch = getString("branch") ?: "",
            commitShort = getString("commitShort") ?: "",
            startedAt = (get("startedAt") as? Number)?.toLong() ?: 0L,
            finishedAt = (get("finishedAt") as? Number)?.toLong(),
            durationMs = (get("durationMs") as? Number)?.toLong(),
            status = getString("status") ?: "succeeded",
            stringsTranslated = (get("stringsTranslated") as? Number)?.toInt() ?: 0,
            stringsPerLocale = perLocale,
            error = getString("error"),
            cacheHits = (get("cacheHits") as? Number)?.toInt() ?: 0,
            tokensIn = (get("tokensIn") as? Number)?.toLong() ?: 0L,
            tokensOut = (get("tokensOut") as? Number)?.toLong() ?: 0L,
            estimatedCostUsd = (get("estimatedCostUsd") as? Number)?.toDouble() ?: 0.0
        )
    }
}
