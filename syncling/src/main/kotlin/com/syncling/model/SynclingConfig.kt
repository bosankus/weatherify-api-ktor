package com.syncling.model

import com.syncling.domain.TargetConfig
import kotlinx.serialization.Serializable

@Serializable
data class SynclingConfig(
    val app: AppConfig,
    val source: SourceConfig,
    val targets: List<TargetConfig>,
    val review: ReviewConfig? = null,
    val glossary: Map<String, Map<String, String>>? = null,
    val notifications: NotificationConfig? = null
)

@Serializable
data class AppConfig(
    val name: String,
    val category: String,
    val tone: String
)

@Serializable
data class SourceConfig(
    val language: String,
    val files: Map<String, String>,
    val watch_branch: String
)

@Serializable
data class ReviewConfig(
    val required_patterns: List<String> = emptyList(),
    val auto_approve_patterns: List<String> = emptyList(),
    val reviewers: List<String> = emptyList()
)

@Serializable
data class NotificationConfig(
    val slack_webhook: String? = null,
    val notify_on: List<String> = emptyList()
)
