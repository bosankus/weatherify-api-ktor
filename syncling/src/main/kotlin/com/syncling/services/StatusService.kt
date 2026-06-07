package com.syncling.services

import com.syncling.domain.PlatformPipelineStats
import com.syncling.domain.PlatformPublishStats
import com.syncling.repository.CdnPublishRepository
import com.syncling.repository.PipelineRunRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Aggregate public status report. Powers /syncling/status (HTML) and
 * /syncling/api/status (JSON). Everything here is safe to expose publicly:
 * no per-customer fields, no project IDs, no repo names.
 */
@Serializable
data class PublicStatusReport(
    /** Generation timestamp in millis since epoch. */
    val generatedAt: Long,
    /** Overall health: "operational" | "degraded" | "outage" — derived from recent run success rate. */
    val overall: String,
    /** Pipeline run stats over the last 24h. Null if pipeline_runs is not wired (older deployments). */
    val pipeline24h: PlatformPipelineStats?,
    val pipeline7d: PlatformPipelineStats?,
    /** CDN publish counts. */
    val publishes24h: PlatformPublishStats,
    val publishes7d: PlatformPublishStats,
    /** Component-level state for the status grid. Each value is one of "operational" | "degraded" | "outage". */
    val components: Map<String, String>,
)

class StatusService(
    private val pipelineRunRepository: PipelineRunRepository?,
    private val cdnPublishRepository: CdnPublishRepository,
    private val cacheTtlMillis: Long = 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val log = LoggerFactory.getLogger(StatusService::class.java)

    private val mutex = Mutex()
    @Volatile private var cached: PublicStatusReport? = null
    @Volatile private var cachedAt: Long = 0L

    suspend fun report(): PublicStatusReport {
        val now = clock()
        cached?.let { if (now - cachedAt < cacheTtlMillis) return it }
        return mutex.withLock {
            val again = cached
            if (again != null && clock() - cachedAt < cacheTtlMillis) return again
            val fresh = compute()
            cached = fresh
            cachedAt = clock()
            fresh
        }
    }

    private suspend fun compute(): PublicStatusReport {
        val now = clock()
        val since24h = now - 24L * 60 * 60 * 1000
        val since7d = now - 7L * 24 * 60 * 60 * 1000

        val pipeline24h = runCatching { pipelineRunRepository?.platformStats(since24h) }
            .onFailure { log.warn("platformStats(24h) failed: {}", it.message) }
            .getOrNull()
        val pipeline7d = runCatching { pipelineRunRepository?.platformStats(since7d) }
            .onFailure { log.warn("platformStats(7d) failed: {}", it.message) }
            .getOrNull()

        val publishes24h = runCatching { cdnPublishRepository.platformPublishStats(since24h) }
            .getOrElse { PlatformPublishStats(24L * 60 * 60 * 1000, 0, 0) }
        val publishes7d = runCatching { cdnPublishRepository.platformPublishStats(since7d) }
            .getOrElse { PlatformPublishStats(7L * 24 * 60 * 60 * 1000, 0, 0) }

        val pipelineState = classifyPipeline(pipeline24h)
        val publishState = classifyPublishes(publishes24h)
        val overall = worstOf(pipelineState, publishState)

        val components = linkedMapOf(
            "API" to "operational",
            "Translation Pipeline" to pipelineState,
            "CDN Delivery" to publishState,
            "GitHub Webhooks" to "operational",
            "Dashboard & Portal" to "operational",
            "Billing" to "operational",
        )

        return PublicStatusReport(
            generatedAt = now,
            overall = overall,
            pipeline24h = pipeline24h,
            pipeline7d = pipeline7d,
            publishes24h = publishes24h,
            publishes7d = publishes7d,
            components = components,
        )
    }

    private fun classifyPipeline(stats: PlatformPipelineStats?): String {
        if (stats == null) return "operational"
        if (stats.totalRuns < 5) return "operational"
        val successRate = stats.succeededRuns.toDouble() / stats.totalRuns
        return when {
            successRate < 0.80 -> "outage"
            successRate < 0.95 -> "degraded"
            else -> "operational"
        }
    }

    private fun classifyPublishes(stats: PlatformPublishStats): String {
        if (stats.totalPublishes < 5) return "operational"
        val ok = stats.succeededPublishes.toDouble() / stats.totalPublishes
        return when {
            ok < 0.80 -> "outage"
            ok < 0.95 -> "degraded"
            else -> "operational"
        }
    }

    private fun worstOf(vararg states: String): String =
        when {
            states.any { it == "outage" } -> "outage"
            states.any { it == "degraded" } -> "degraded"
            else -> "operational"
        }
}
