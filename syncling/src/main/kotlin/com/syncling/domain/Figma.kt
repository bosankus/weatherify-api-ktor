package com.syncling.domain

import kotlinx.datetime.Instant

enum class FigmaCandidateStatus {
    /** Waiting in the inbox for a dev to approve, edit, or reject. */
    PENDING,
    /** Approved — included in an open PR against the project's watch branch. */
    PR_OPEN,
    /** Rejected from the inbox; kept for audit, never re-suggested for the same text. */
    REJECTED,
}

/**
 * One text node pushed from the Figma plugin, staged in the project's Figma inbox.
 * Approval merges the string into the source file and opens a PR; the merge of that
 * PR triggers the regular translation pipeline via the existing push webhook.
 */
data class FigmaStringCandidate(
    val id: String,
    val projectId: String,
    val figmaFileKey: String,
    val figmaNodeId: String,
    /** Figma layer name — used as a key-suggestion hint and shown in the inbox. */
    val nodeName: String,
    val pageName: String? = null,
    val frameName: String? = null,
    /** Nearest Figma frame node id — keys the shared frame screenshot in [FigmaFramePreview]. */
    val figmaFrameId: String? = null,
    val sourceText: String,
    /** AI-suggested snake_case key (or slug fallback when suggestion fails). */
    val suggestedKey: String,
    /** Dev-edited key; overrides [suggestedKey] when set. */
    val finalKey: String? = null,
    val status: FigmaCandidateStatus = FigmaCandidateStatus.PENDING,
    /** Existing source key whose text is identical — the inbox suggests reusing it instead of minting a new key. */
    val duplicateOfKey: String? = null,
    /** Existing source key whose text is *semantically* close (embedding cosine) — softer reuse hint than [duplicateOfKey]. */
    val similarToKey: String? = null,
    /** Cosine similarity (0..1) behind [similarToKey]. */
    val similarityScore: Float? = null,
    /**
     * Set when this node is already bound to a repo key from a previous sync.
     * Approval then updates that key's text in place instead of adding a new key.
     */
    val boundKey: String? = null,
    val submittedByUserId: String,
    val prUrl: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** The key that approval will write: bound key wins, then the dev's edit, then the suggestion. */
    val effectiveKey: String get() = boundKey ?: finalKey ?: suggestedKey
}

/**
 * Persistent mapping between a Figma text node and the string key it produced.
 * This is what turns a designer's later copy edit into an update of the existing
 * key rather than a duplicate new key.
 */
/**
 * Screenshot of a Figma frame, shared by every candidate extracted from that frame.
 * Gives the reviewing dev (and later the translator) the visual context of the string.
 */
data class FigmaFramePreview(
    val projectId: String,
    val figmaFileKey: String,
    val figmaFrameId: String,
    /** Scaled-down PNG exported by the plugin. */
    val png: ByteArray,
    val updatedAt: Instant,
) {
    override fun equals(other: Any?): Boolean =
        other is FigmaFramePreview && projectId == other.projectId &&
            figmaFileKey == other.figmaFileKey && figmaFrameId == other.figmaFrameId
    override fun hashCode(): Int =
        ((projectId.hashCode() * 31) + figmaFileKey.hashCode()) * 31 + figmaFrameId.hashCode()
}

data class FigmaNodeBinding(
    val projectId: String,
    val figmaFileKey: String,
    val figmaNodeId: String,
    val stringKey: String,
    /** Text at the time of the last approved sync — used to detect unchanged re-pushes. */
    val lastText: String,
    val updatedAt: Instant,
)
