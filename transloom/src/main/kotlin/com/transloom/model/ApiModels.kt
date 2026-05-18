package com.transloom.model

import com.transloom.domain.TargetConfig
import kotlinx.serialization.Serializable

@Serializable
data class CreateProjectBody(
    val name: String,
    val githubRepo: String,
    val watchBranch: String = "main",
    val sourceFilePath: String = "values/strings.xml",
    val category: String,
    val tone: String,
    val targets: List<TargetConfig>
)

@Serializable
data class ProjectResponse(
    val id: String,
    val name: String,
    val githubRepo: String,
    val watchBranch: String,
    val sourceFilePath: String,
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
    val status: String,
    val blockReason: String?,
    val projectId: String,
    val projectName: String
)

@Serializable
data class ReviewListResponse(val pending_reviews: List<ReviewItemResponse>, val count: Int)

@Serializable
data class RejectBody(val reason: String)

@Serializable
data class ApproveBody(val editedText: String? = null)

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
    val sourceFilePath: String? = null,
    val targets: List<TargetConfig>? = null,
    val culturalSensitivityEnabled: Boolean? = null
)

@Serializable
data class ProjectDetailResponse(
    val id: String,
    val name: String,
    val githubRepo: String,
    val watchBranch: String,
    val sourceFilePath: String,
    val category: String,
    val tone: String,
    val targets: List<TargetConfig>,
    val culturalSensitivityEnabled: Boolean = false
)

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
