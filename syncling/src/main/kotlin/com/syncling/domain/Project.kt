package com.syncling.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Event keys a project can subscribe its Slack/Teams webhooks to. All enabled by default. */
val DEFAULT_CHAT_NOTIFY_EVENTS: List<String> = listOf("run_completed", "run_failed", "quota_exceeded", "billing")

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
    val lastSourceFileHash: String? = null,
    /** When true, pipeline publishes translation bundles to the CDN (OTA) at the end of each run. */
    val otaEnabled: Boolean = false,
    /** When true, a successful publish is auto-promoted to the active version. When false, devs must promote manually. */
    val autoPromote: Boolean = true,
    /** Custom PR branch name pattern. Supports {timestamp}, {date}, {branch} tokens. Solo/Team only. */
    val prBranchPattern: String? = null,
    /** URL to POST outbound webhook payloads to after each pipeline run. Solo/Team only. */
    val outboundWebhookUrl: String? = null,
    /** HMAC-SHA256 signing secret for outbound webhook payloads. Stored encrypted. */
    val outboundWebhookSecret: String? = null,
    /** Slack incoming-webhook URL to post chat notifications to. Solo/Team only. */
    val slackWebhookUrl: String? = null,
    /** Microsoft Teams (Power Automate Workflows) webhook URL to post chat notifications to. Solo/Team only. */
    val teamsWebhookUrl: String? = null,
    /** Which event keys are delivered to the chat webhooks. See [DEFAULT_CHAT_NOTIFY_EVENTS]. */
    val chatNotifyEvents: List<String> = DEFAULT_CHAT_NOTIFY_EVENTS,
    /**
     * Per-project monthly string cap (independent of plan-level quota). When set, the pipeline
     * hard-stops for this project once this many strings have been translated in the current month,
     * even if the owner's plan quota has headroom. Solo/Team only. Null = no project-level cap.
     */
    val monthlyStringQuota: Int? = null,
    /**
     * CDN canary rollout percentage (0–100). When < 100 and OTA is enabled, a new publish writes
     * the `canary` pointer instead of `active`. Client SDKs receive the canary bundle when
     * hash(userId) % 100 < rolloutPercent. 100 = full rollout (writes `active`). Solo/Team only.
     */
    val rolloutPercent: Int = 100
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
    val sharedMemoryOptIn: Boolean = false,
    val otaEnabled: Boolean = false,
    val autoPromote: Boolean = true,
    val prBranchPattern: String? = null
)
