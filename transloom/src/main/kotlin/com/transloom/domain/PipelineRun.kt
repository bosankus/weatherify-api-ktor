package com.transloom.domain

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
    val error: String? = null,
    val projectId: String? = null,
    val retriedFromRunId: String? = null
)

@Serializable
data class PipelineStepState(
    val id: String,
    val label: String,
    val status: String, // pending | running | done | error | skipped
    val detail: String? = null
)

val STEP_LABELS = mapOf(
    "WEBHOOK_RECEIVED" to "Push detected",
    "FETCHING_STRINGS" to "Reading source file",
    "DETECTING_CHANGES" to "Scanning for changes",
    "BILLING_CHECK" to "Checking plan limits",
    "TRANSLATING" to "Translating strings",
    "CREATING_PR" to "Opening pull request"
)

fun initialSteps(): List<PipelineStepState> = STEP_LABELS.map { (id, label) ->
    PipelineStepState(id = id, label = label, status = if (id == "WEBHOOK_RECEIVED") "done" else "pending")
}
