package com.syncling.services

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Pipeline-wide metrics. Wraps a [MeterRegistry] (Micrometer) so the underlying backend
 * (Prometheus today, anything tomorrow) is interchangeable.
 *
 * Naming convention follows Prometheus best practices:
 *   • `syncling_pipeline_stage_duration_seconds{stage="…",outcome="ok|error"}` — histogram
 *   • `syncling_pipeline_runs_total{status="…"}` — counter
 *   • `syncling_pipeline_strings_total{kind="translated|cache_hit|surface_skipped|blocked"}` — counter
 *   • `syncling_gemini_tokens_total{direction="in|out", endpoint="…"}` — counter
 *   • `syncling_gemini_cost_usd_total{endpoint="…"}` — counter (dollars × 1)
 *
 * All meters are looked up lazily and cached so we don't spam the registry with the same tag set.
 */
class PipelineMetrics(private val registry: MeterRegistry) {

    private val stageTimers = ConcurrentHashMap<String, Timer>()
    private val runCounters = ConcurrentHashMap<String, Counter>()
    private val stringCounters = ConcurrentHashMap<String, Counter>()
    private val tokenCounters = ConcurrentHashMap<String, Counter>()
    private val costCounters = ConcurrentHashMap<String, Counter>()

    /** Record stage latency. [outcome] is `"ok"` or `"error"` so dashboards can split error budgets. */
    fun recordStage(stage: String, durationNanos: Long, outcome: String) {
        stageTimers.computeIfAbsent("$stage|$outcome") {
            Timer.builder("syncling_pipeline_stage_duration_seconds")
                .description("Pipeline stage execution duration")
                .tag("stage", stage)
                .tag("outcome", outcome)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
        }.record(durationNanos, TimeUnit.NANOSECONDS)
    }

    /** Count a finished run by terminal status (`succeeded` | `failed` | `quota_exceeded`). */
    fun incrementRun(status: String) {
        runCounters.computeIfAbsent(status) {
            Counter.builder("syncling_pipeline_runs_total")
                .description("Pipeline run terminations")
                .tag("status", status)
                .register(registry)
        }.increment()
    }

    /** Track per-string outcomes — `translated` (Gemini), `cache_hit`, `surface_skipped`, `blocked`. */
    fun addStrings(kind: String, count: Long) {
        if (count <= 0) return
        stringCounters.computeIfAbsent(kind) {
            Counter.builder("syncling_pipeline_strings_total")
                .description("Per-string outcomes across all pipeline runs")
                .tag("kind", kind)
                .register(registry)
        }.increment(count.toDouble())
    }

    /** Add tokens consumed by a Gemini call. */
    fun addTokens(direction: String, endpoint: String, count: Long) {
        if (count <= 0) return
        tokenCounters.computeIfAbsent("$direction|$endpoint") {
            Counter.builder("syncling_gemini_tokens_total")
                .description("Tokens consumed by Gemini calls")
                .tag("direction", direction)
                .tag("endpoint", endpoint)
                .register(registry)
        }.increment(count.toDouble())
    }

    /** Add estimated USD cost for a Gemini call. */
    fun addCostUsd(endpoint: String, dollars: Double) {
        if (dollars <= 0.0) return
        costCounters.computeIfAbsent(endpoint) {
            Counter.builder("syncling_gemini_cost_usd_total")
                .description("Estimated Gemini cost in USD")
                .tag("endpoint", endpoint)
                .register(registry)
        }.increment(dollars)
    }

    /**
     * Wraps [block] with a timer that records latency + outcome. Re-throws any exception
     * after marking the outcome as `"error"`, so callers don't have to remember the pairing.
     */
    suspend fun <T> timeStage(stage: String, block: suspend () -> T): T {
        val start = System.nanoTime()
        var outcome = "ok"
        try {
            return block()
        } catch (e: Throwable) {
            outcome = "error"
            throw e
        } finally {
            recordStage(stage, System.nanoTime() - start, outcome)
        }
    }
}
