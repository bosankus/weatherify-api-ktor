package com.transloom.services

import com.transloom.domain.BillingPlan
import com.transloom.domain.NotificationType
import com.transloom.repository.NotificationRepository
import org.slf4j.LoggerFactory

private const val DEDUP_6H = 6 * 60 * 60 * 1000L
private const val DEDUP_1H = 60 * 60 * 1000L

/**
 * Creates persistent in-app notifications and pushes them to the user's live SSE
 * connection so the dashboard bell updates in real-time without a page reload.
 */
class InAppNotificationService(
    private val notificationRepository: NotificationRepository,
    private val eventBus: PipelineEventBus
) {
    private val log = LoggerFactory.getLogger(InAppNotificationService::class.java)

    suspend fun notifyPipelineComplete(
        userId: String,
        projectName: String,
        prUrl: String,
        langDetail: String,
        commitShort: String
    ) = notify(
        userId = userId,
        type = NotificationType.PIPELINE_COMPLETE,
        title = "Translations ready — $projectName",
        message = "$langDetail translated · commit $commitShort",
        level = "success",
        actionUrl = prUrl,
        actionLabel = "View PR",
        dedupMs = DEDUP_1H
    )

    suspend fun notifyReviewQueue(userId: String, pendingCount: Int) = notify(
        userId = userId,
        type = NotificationType.REVIEW_QUEUE,
        title = "$pendingCount string${if (pendingCount != 1) "s" else ""} need your review",
        message = "Approve or reject flagged translations to unblock the next PR.",
        level = "warning",
        actionUrl = "/transloom/review-portal",
        actionLabel = "Review now",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyTrialLimitHit(userId: String) = notify(
        userId = userId,
        type = NotificationType.TRIAL_LIMIT,
        title = "Free plan limit reached",
        message = "You've used all 500 free strings this month. Upgrade to keep translating.",
        level = "error",
        actionUrl = "/transloom/billing",
        actionLabel = "Upgrade",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyCheckoutAbandoned(userId: String, pendingPlan: BillingPlan) = notify(
        userId = userId,
        type = NotificationType.CHECKOUT_ABANDONED,
        title = "Your ${pendingPlan.displayName} trial is waiting",
        message = "You started a free trial but didn't finish. No charge until the trial ends.",
        level = "warning",
        actionUrl = "/transloom/billing",
        actionLabel = "Complete setup",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyPlanExpiry(userId: String, daysLeft: Long, plan: BillingPlan) = notify(
        userId = userId,
        type = NotificationType.PLAN_EXPIRY,
        title = "${plan.displayName} plan renews in $daysLeft day${if (daysLeft != 1L) "s" else ""}",
        message = "Make sure your payment method is up to date to avoid interruption.",
        level = "warning",
        actionUrl = "/transloom/billing",
        actionLabel = "Manage billing",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyGitHubTokenInvalid(userId: String, repo: String) = notify(
        userId = userId,
        type = NotificationType.GITHUB_TOKEN_INVALID,
        title = "GitHub access lost — re-authenticate",
        message = "Transloom can no longer access $repo. Re-connect GitHub to resume automatic translations.",
        level = "error",
        actionUrl = "/transloom/auth/github",
        actionLabel = "Re-connect GitHub",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyPipelineFailed(userId: String, repo: String, reason: String) = notify(
        userId = userId,
        type = NotificationType.PIPELINE_FAILED,
        title = "Pipeline failed — $repo",
        message = reason,
        level = "error",
        actionUrl = "/transloom/app#activity",
        actionLabel = "View details",
        dedupMs = DEDUP_1H
    )

    suspend fun notifyOnboarding(userId: String, stuckReason: String) = notify(
        userId = userId,
        type = NotificationType.ONBOARDING,
        title = "Pick up where you left off",
        message = stuckReason,
        level = "info",
        actionUrl = "/transloom/app",
        actionLabel = "Continue setup",
        dedupMs = DEDUP_6H
    )

    // ── Core create + push ─────────────────────────────────────────────────────

    private suspend fun notify(
        userId: String,
        type: String,
        title: String,
        message: String,
        level: String,
        actionUrl: String?,
        actionLabel: String?,
        dedupMs: Long
    ) {
        runCatching {
            if (notificationRepository.existsRecent(userId, type, dedupMs)) {
                log.debug("Skipping duplicate in-app notification: userId={} type={}", userId, type)
                return
            }
            val notif = notificationRepository.create(userId, type, title, message, level, actionUrl, actionLabel)
            eventBus.emitNotification(userId, notif.id, title, message, level, actionUrl, actionLabel)
            log.debug("In-app notification created: userId={} type={} id={}", userId, type, notif.id)
        }.onFailure {
            log.error("In-app notification failed: userId={} type={} error={}", userId, type, it.message)
        }
    }
}
