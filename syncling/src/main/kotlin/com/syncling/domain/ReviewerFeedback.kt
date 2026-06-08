package com.syncling.domain

/**
 * One reviewer correction captured at approve-with-edit or hotfix time.
 *
 * The triple (sourceText, modelOutput, reviewerEdit) is the gold-standard supervision signal
 * we feed back into the Gemini prompt as few-shot examples. Without this loop every reviewer
 * edit evaporates the moment the PR merges.
 */
data class ReviewerFeedback(
    val id: String,
    val projectId: String,
    val targetLanguage: String,
    val sourceText: String,
    /** What the model produced before the reviewer touched it. */
    val modelOutput: String,
    /** What the reviewer actually shipped. */
    val reviewerEdit: String,
    /** Free-text reason captured on reject; null otherwise. */
    val reason: String? = null,
    val createdAt: Long
)
