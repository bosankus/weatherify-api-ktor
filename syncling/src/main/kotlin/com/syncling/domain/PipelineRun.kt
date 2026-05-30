package com.syncling.domain

import kotlinx.serialization.Serializable

@Serializable
data class PipelineRunState(
    val runId: String,
    val repo: String,
    val branch: String,
    val commitShort: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val steps: List<PipelineStepState>,
    val prUrl: String? = null,
    val prBranch: String? = null,
    val error: String? = null,
    val projectId: String? = null,
    val ownerId: String? = null,
    val retriedFromRunId: String? = null,
    val surfaceSkipped: Int = 0,
    val retryCount: Int = 0,
    // Per-locale lanes and aggregate progress for the TRANSLATING phase.
    // Populated incrementally as batches complete so the dashboard can show
    // a live ETA and per-language status without polling.
    val locales: List<LocaleProgressState> = emptyList(),
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    // Who caused this run. For webhook pushes, populated by resolving the
    // commit author's email against project_members; null means we couldn't
    // match the email to an active member (= an "external" actor, attributed
    // to the project owner for billing but distinguished in analytics).
    val triggeredByUserId: String? = null,
    val triggeredByLabel: String = "external"
)

@Serializable
data class PipelineStepState(
    val id: String,
    val label: String,
    val status: String, // pending | running | done | error | skipped
    val detail: String? = null
)

@Serializable
data class LocaleProgressState(
    val code: String,
    val name: String,
    val status: String, // queued | translating | done | error
    val done: Int = 0,
    val total: Int = 0
)

val STEP_LABELS = mapOf(
    "WEBHOOK_RECEIVED" to "Push detected",
    "FETCHING_STRINGS" to "Reading source file",
    "DETECTING_CHANGES" to "Scanning for changes",
    "BILLING_CHECK" to "Checking plan limits",
    "TRANSLATING" to "Translating strings",
    "CREATING_PR" to "Opening pull request",
    "CDN_PUBLISH" to "Publishing to CDN"
)

fun initialSteps(): List<PipelineStepState> = STEP_LABELS.map { (id, label) ->
    PipelineStepState(id = id, label = label, status = if (id == "WEBHOOK_RECEIVED") "done" else "pending")
}
