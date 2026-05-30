package com.syncling.model

import com.syncling.domain.TargetConfig
import kotlinx.serialization.Serializable

@Serializable
data class CreateProjectBody(
    val name: String,
    val githubRepo: String,
    val watchBranch: String = "main",
    val sourceFilePaths: List<String> = listOf("values/strings.xml"),
    val category: String,
    val tone: String,
    val targets: List<TargetConfig>,
    val sharedMemoryOptIn: Boolean = false,
    val prBranchPattern: String? = null
)

@Serializable
data class ProjectResponse(
    val id: String,
    val name: String,
    val githubRepo: String,
    val watchBranch: String,
    val sourceFilePaths: List<String>,
    val category: String,
    val tone: String,
    val targetCount: Int
)

@Serializable
data class ReviewItemResponse(
    val id: String,
    val stringKey: String,
    val sourceText: String,
    val targetLanguage: String,
    val targetRegion: String?,
    val translatedText: String,
    /** Non-null when a string was retranslated — allows the reviewer to diff old vs new output. */
    val previousTranslatedText: String? = null,
    val status: String,
    val blockReason: String?,
    val lockedAt: Long? = null,
    val lockedBy: String? = null,
    val projectId: String,
    val projectName: String,
    val pipelineRunId: String? = null,
    val commitShort: String? = null,
    /** True when the owning project has OTA publish enabled — gates the Hotfix action in the review portal. */
    val projectOtaEnabled: Boolean = false
)

@Serializable
data class ReviewListResponse(val pending_reviews: List<ReviewItemResponse>, val count: Int)

@Serializable
data class RejectBody(val reason: String)

@Serializable
data class ApproveBody(val editedText: String? = null)

@Serializable
data class HotfixBody(val newText: String)

@Serializable
data class HotfixResponse(
    val id: String,
    val translatedText: String,
    val publish: PublishReceiptInline?
)

@Serializable
data class PublishReceiptInline(
    val bundleVersion: String,
    val locales: List<String>,
    val promoted: Boolean,
    val skipped: Boolean = false
)

@Serializable
data class GlossaryEntryBody(
    val languageCode: String,
    val sourceTerm: String,
    val targetTerm: String
)

@Serializable
data class GlossaryEntryResponse(
    val id: String,
    val languageCode: String,
    val sourceTerm: String,
    val targetTerm: String
)

@Serializable
data class UpdateProjectBody(
    val name: String? = null,
    val tone: String? = null,
    val category: String? = null,
    val watchBranch: String? = null,
    val sourceFilePaths: List<String>? = null,
    val targets: List<TargetConfig>? = null,
    val culturalSensitivityEnabled: Boolean? = null,
    val autoApproveEnabled: Boolean? = null,
    val otaEnabled: Boolean? = null,
    val autoPromote: Boolean? = null,
    val sharedMemoryOptIn: Boolean? = null,
    val prBranchPattern: String? = null,
    /** Set to "" to clear the webhook URL. Solo/Team only. */
    val outboundWebhookUrl: String? = null,
    /** Set to "" to clear the signing secret. Solo/Team only. */
    val outboundWebhookSecret: String? = null,
    /** Set to -1 to remove the project-level cap. Solo/Team only. */
    val monthlyStringQuota: Int? = null,
    /** 0–100. 100 = full traffic to active; 1–99 = canary rollout. Solo/Team only. */
    val rolloutPercent: Int? = null
)

@Serializable
data class ProjectDetailResponse(
    val id: String,
    val name: String,
    val githubRepo: String,
    val watchBranch: String,
    val sourceFilePaths: List<String>,
    val category: String,
    val tone: String,
    val targets: List<TargetConfig>,
    val culturalSensitivityEnabled: Boolean = false,
    val autoApproveEnabled: Boolean = false,
    val sharedMemoryOptIn: Boolean = false,
    val otaEnabled: Boolean = false,
    val autoPromote: Boolean = true,
    /** ISO-8601 date-time of the last successful webhook verification, or null if never verified. */
    val webhookVerifiedAt: String? = null,
    val prBranchPattern: String? = null,
    /** Configured outbound webhook URL, or null if not set. Secret is never returned. */
    val outboundWebhookUrl: String? = null,
    val monthlyStringQuota: Int? = null,
    val rolloutPercent: Int = 100
)

@Serializable
data class BatchApproveBody(val ids: List<String>)

@Serializable
data class ApiError(val error: String)

/**
 * Structured error envelope for cases where the UI needs to act on the failure
 * (e.g. send the user back to GitHub OAuth) rather than just show a toast.
 */
@Serializable
data class StructuredApiError(
    val error: String,
    val code: String,
    val actionHint: String? = null,
    val reauthUrl: String? = null
)
