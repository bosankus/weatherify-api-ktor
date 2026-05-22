package com.transloom.domain

import kotlinx.serialization.Serializable

@Serializable
data class TranslationHistoryEntry(
    val id: String,
    val translationId: String,
    val stringKey: String,
    val projectId: String,
    val targetLanguage: String,
    val previousText: String,
    val newText: String,
    val changedAt: Long,
    /** "pipeline" for automated runs, userId for manual edits/approvals. */
    val changedBy: String,
    val pipelineRunId: String? = null
)
