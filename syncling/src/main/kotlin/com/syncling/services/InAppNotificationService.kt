package com.syncling.services

import com.syncling.domain.BillingPlan
import com.syncling.domain.NotificationType
import com.syncling.repository.NotificationRepository
import org.slf4j.LoggerFactory

private const val DEDUP_6H = 6 * 60 * 60 * 1000L
private const val DEDUP_1H = 60 * 60 * 1000L

/**
 * Creates persistent in-app notifications and pushes them to the user's live SSE
 * connection so the dashboard bell updates in real-time without a page reload.
 */
class InAppNotificationService(
    private val notificationRepository: NotificationRepository,
    private val eventBus: PipelineEventBus,
    /** Optional Slack/Teams fan-out — every in-app notification is mirrored to chat when set. */
    private val chatNotificationService: ChatNotificationService? = null
) {
    private val log = LoggerFactory.getLogger(InAppNotificationService::class.java)

    suspend fun notifyPipelineComplete(
        userId: String,
        projectName: String,
        prUrl: String,
        langDetail: String,
        commitShort: String,
        projectId: String? = null
    ) = notify(
        userId = userId,
        type = NotificationType.PIPELINE_COMPLETE,
        title = "Translations ready — $projectName",
        message = "$langDetail translated · commit $commitShort",
        level = "success",
        actionUrl = prUrl,
        actionLabel = "View PR",
        dedupMs = DEDUP_1H,
        projectId = projectId
    )

    suspend fun notifyReviewQueue(userId: String, pendingCount: Int) = notify(
        userId = userId,
        type = NotificationType.REVIEW_QUEUE,
        title = "$pendingCount string${if (pendingCount != 1) "s" else ""} need your review",
        message = "Approve or reject flagged translations to unblock the next PR.",
        level = "warning",
        actionUrl = "/review-portal",
        actionLabel = "Review now",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyTrialLimitHit(userId: String) = notify(
        userId = userId,
        type = NotificationType.TRIAL_LIMIT,
        title = "Free plan limit reached",
        message = "You've used all 500 free strings this month. Upgrade to keep translating.",
        level = "error",
        actionUrl = "/billing",
        actionLabel = "Upgrade",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyCheckoutAbandoned(userId: String, pendingPlan: BillingPlan) = notify(
        userId = userId,
        type = NotificationType.CHECKOUT_ABANDONED,
        title = "Your ${pendingPlan.displayName} trial is waiting",
        message = "You started a free trial but didn't finish. No charge until the trial ends.",
        level = "warning",
        actionUrl = "/billing",
        actionLabel = "Complete setup",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyPlanExpiry(userId: String, daysLeft: Long, plan: BillingPlan) = notify(
        userId = userId,
        type = NotificationType.PLAN_EXPIRY,
        title = "${plan.displayName} plan renews in $daysLeft day${if (daysLeft != 1L) "s" else ""}",
        message = "Make sure your payment method is up to date to avoid interruption.",
        level = "warning",
        actionUrl = "/billing",
        actionLabel = "Manage billing",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyGitHubTokenInvalid(userId: String, repo: String, projectId: String? = null) = notify(
        userId = userId,
        type = NotificationType.GITHUB_TOKEN_INVALID,
        title = "GitHub access lost — re-authenticate",
        message = "Syncling can no longer access $repo. Re-connect GitHub to resume automatic translations.",
        level = "error",
        actionUrl = "/auth/github",
        actionLabel = "Re-connect GitHub",
        dedupMs = DEDUP_6H,
        projectId = projectId
    )

    suspend fun notifyPipelineFailed(userId: String, repo: String, reason: String, projectId: String? = null) = notify(
        userId = userId,
        type = NotificationType.PIPELINE_FAILED,
        title = "Pipeline failed — $repo",
        message = reason,
        level = "error",
        actionUrl = "/app#activity",
        actionLabel = "View details",
        dedupMs = DEDUP_1H,
        projectId = projectId
    )

    suspend fun notifyOnboarding(userId: String, stuckReason: String) = notify(
        userId = userId,
        type = NotificationType.ONBOARDING,
        title = "Pick up where you left off",
        message = stuckReason,
        level = "info",
        actionUrl = "/app",
        actionLabel = "Continue setup",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyTrialStarted(userId: String, planDisplayName: String, trialEndsOn: String) = notify(
        userId = userId,
        type = NotificationType.TRIAL_STARTED,
        title = "$planDisplayName trial activated",
        message = "Your 7-day free trial is live. First charge on $trialEndsOn — cancel any time before then.",
        level = "success",
        actionUrl = "/billing",
        actionLabel = "View billing",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyPaymentFailed(userId: String, planDisplayName: String) = notify(
        userId = userId,
        type = NotificationType.PAYMENT_FAILED,
        title = "Payment failed — action required",
        message = "We couldn't charge your card for the $planDisplayName plan. Update your payment method to keep translating.",
        level = "error",
        actionUrl = "/billing",
        actionLabel = "Update payment",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyPaymentReceived(userId: String, amount: String, planDisplayName: String) = notify(
        userId = userId,
        type = NotificationType.PAYMENT_RECEIVED,
        title = "Payment received — $planDisplayName renewed",
        message = "$amount charged successfully. Your subscription is active for another month.",
        level = "success",
        actionUrl = "/billing",
        actionLabel = "View invoices",
        dedupMs = DEDUP_1H
    )

    suspend fun notifySubscriptionEnded(userId: String) = notify(
        userId = userId,
        type = NotificationType.SUBSCRIPTION_ENDED,
        title = "Subscription ended — downgraded to Free",
        message = "Your paid plan has ended. Upgrade any time to resume automatic translations.",
        level = "warning",
        actionUrl = "/billing",
        actionLabel = "Upgrade",
        dedupMs = DEDUP_6H
    )

    suspend fun notifyTranslationsResumed(
        userId: String,
        projects: Int,
        strings: Int,
        trigger: String
    ) = notify(
        userId = userId,
        type = NotificationType.TRANSLATIONS_RESUMED,
        title = "Translations resumed",
        message = buildString {
            append(
                if (trigger == "quota_reset") "Your monthly quota has reset. "
                else "Your plan is active. "
            )
            append("$projects blocked project${if (projects != 1) "s" else ""} ")
            if (strings > 0) append("(≈$strings string${if (strings != 1) "s" else ""}) ")
            append("are being translated now — no re-push needed.")
        },
        level = "success",
        actionUrl = "/app#activity",
        actionLabel = "Watch progress",
        dedupMs = DEDUP_1H
    )

    suspend fun notifyFigmaStrings(userId: String, projectName: String, projectId: String, count: Int) = notify(
        userId = userId,
        type = NotificationType.FIGMA_STRINGS,
        title = "$count Figma string${if (count != 1) "s" else ""} awaiting review — $projectName",
        message = "New copy was pushed from Figma. Approve to open a PR that adds the strings to your source file.",
        level = "info",
        actionUrl = "/figma/$projectId",
        actionLabel = "Open inbox",
        dedupMs = DEDUP_1H,
        projectId = projectId
    )

    suspend fun notifyFigmaAutoPr(userId: String, projectName: String, projectId: String, count: Int, prUrl: String) = notify(
        userId = userId,
        type = NotificationType.FIGMA_AUTO_PR,
        title = "PR opened from Figma — $projectName",
        message = "Auto-approve turned $count pushed string${if (count != 1) "s" else ""} into a PR. Merge it to translate.",
        level = "success",
        actionUrl = prUrl,
        actionLabel = "View PR",
        dedupMs = DEDUP_1H,
        projectId = projectId
    )

    suspend fun notifyInviteAccepted(
        ownerId: String,
        memberName: String,
        projectName: String,
        projectId: String
    ) = notify(
        userId = ownerId,
        type = NotificationType.INVITE_ACCEPTED,
        title = "$memberName joined $projectName",
        message = "They now have access based on the role you assigned.",
        level = "success",
        actionUrl = "/members/$projectId",
        actionLabel = "Manage members",
        dedupMs = 0
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
        dedupMs: Long,
        /** Project context when the event is project-scoped (pipeline runs). Null = account-level. */
        projectId: String? = null
    ) {
        runCatching {
            if (notificationRepository.existsRecent(userId, type, dedupMs)) {
                log.debug("Skipping duplicate in-app notification: userId={} type={}", userId, type)
                return
            }
            val notif = notificationRepository.create(userId, type, title, message, level, actionUrl, actionLabel)
            eventBus.emitNotification(userId, notif.id, title, message, level, actionUrl, actionLabel)
            log.debug("In-app notification created: userId={} type={} id={}", userId, type, notif.id)
            // Mirror to Slack/Teams. Runs on the chat service's own scope — never blocks
            // or fails the in-app path (and stays behind the dedup gate above).
            chatNotificationService?.fireAndForget(userId, projectId, type, title, message)
        }.onFailure {
            log.error("In-app notification failed: userId={} type={} error={}", userId, type, it.message)
        }
    }
}
