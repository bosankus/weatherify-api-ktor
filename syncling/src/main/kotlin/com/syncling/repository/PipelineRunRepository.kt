package com.syncling.repository

import com.syncling.domain.PipelineRunSummary
import com.syncling.domain.PlatformPipelineStats

interface PipelineRunRepository {
    /** Idempotent — re-finishing a run with the same id overwrites the previous summary. */
    suspend fun persist(summary: PipelineRunSummary)

    suspend fun listForOwner(ownerId: String, sinceMillis: Long, limit: Int = 200): List<PipelineRunSummary>

    suspend fun listForProject(projectId: String, sinceMillis: Long, limit: Int = 200): List<PipelineRunSummary>

    suspend fun listForMember(memberUserId: String, sinceMillis: Long, limit: Int = 200): List<PipelineRunSummary>

    /** Earliest startedAt across all runs for this owner. Null if the owner has no runs. */
    suspend fun earliestStartedAtForOwner(ownerId: String): Long?

    /**
     * Aggregate stats across all runs started since [sinceMillis]. Used by the
     * public status page to back the marketing performance claims.
     * Percentiles are computed over runs that finished successfully (finishedAt != null
     * and status == "succeeded").
     */
    suspend fun platformStats(sinceMillis: Long): PlatformPipelineStats
}
