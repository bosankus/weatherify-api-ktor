package com.transloom.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

data class Project(
    val id: String,
    val ownerId: String,
    val name: String,
    val githubRepo: String,
    val watchBranch: String,
    val sourceFilePaths: List<String>,
    val category: String,
    val tone: String,
    val targets: List<TargetConfig> = emptyList(),
    val culturalSensitivityEnabled: Boolean = false,
    /** When true, translations that pass cultural sensitivity checks are approved automatically without human review. */
    val autoApproveEnabled: Boolean = false,
    /** When true, auto-approved translations are contributed to the anonymised shared translation memory pool. */
    val sharedMemoryOptIn: Boolean = false,
    /** Timestamp of the last successful webhook verification. Null means never verified. */
    val webhookVerifiedAt: Instant? = null,
    /** SHA-256 of all source file contents combined after the last fully successful pipeline run. */
    val lastSourceFileHash: String? = null
)

@Serializable
data class TargetConfig(
    val code: String,
    val name: String,
    val region: String? = null,
    val file: String
)

data class CreateProjectInput(
    val name: String,
    val githubRepo: String,
    val watchBranch: String = "main",
    val sourceFilePaths: List<String> = listOf("values/strings.xml"),
    val category: String,
    val tone: String,
    val targets: List<TargetConfig>,
    val culturalSensitivityEnabled: Boolean = false,
    val autoApproveEnabled: Boolean = false,
    val sharedMemoryOptIn: Boolean = false
)
