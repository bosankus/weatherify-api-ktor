package com.syncling.domain

import kotlinx.serialization.Serializable

/**
 * Aggregate platform-wide pipeline metrics over a single rolling time window.
 * No per-customer fields — safe for the public status page.
 */
@Serializable
data class PlatformPipelineStats(
    val windowMillis: Long,
    val totalRuns: Int,
    val succeededRuns: Int,
    val failedRuns: Int,
    val durationP50Ms: Long?,
    val durationP95Ms: Long?,
    val durationP99Ms: Long?,
    val totalStringsTranslated: Long,
)

@Serializable
data class PlatformPublishStats(
    val windowMillis: Long,
    val totalPublishes: Int,
    val succeededPublishes: Int,
)
