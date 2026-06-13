package com.syncling.services

import com.syncling.domain.UserEvent
import com.syncling.queue.TranslationJobQueue
import com.syncling.queue.WebhookPayload
import com.syncling.repository.BillingRepository
import com.syncling.repository.QuotaBlockedRunRepository
import com.syncling.repository.UserRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

/**
 * Lifts a quota hold and re-enqueues every pipeline run that was blocked by it.
 *
 * Quota holds end three ways — plan upgrade (rp-callback / subscription.charged webhook),
 * trial activation, and the monthly usage reset. All paths converge here so the user never
 * has to re-push: each blocked project is re-run at the current HEAD of its watched branch,
 * which also covers any pushes GitHub delivered (and we dropped) while the hold was active.
 */
class QuotaResumeService(
    private val billingRepository: BillingRepository,
    private val blockedRunRepository: QuotaBlockedRunRepository,
    private val userRepository: UserRepository,
    private val githubService: GitHubService,
    private val jobQueue: TranslationJobQueue,
    private val inAppNotificationService: InAppNotificationService? = null,
    private val userActivityService: UserActivityService? = null
) {
    private val log = LoggerFactory.getLogger(QuotaResumeService::class.java)

    suspend fun resumeAfterUpgrade(userId: String) = resume(userId, trigger = "upgrade")

    /**
     * Scans for holds set in a previous UTC month (usage counters are month-keyed, so those
     * quotas have reset) and resumes each affected user. Called from the lifecycle monitor.
     * Returns the number of users whose holds were lifted.
     */
    suspend fun resumeExpiredHolds(): Int {
        val nowDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val monthStart = LocalDate(nowDate.year, nowDate.month, 1)
            .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val users = runCatching { billingRepository.findExpiredLimitHolds(monthStart) }
            .getOrElse { log.warn("Expired-hold scan failed: {}", it.message); return 0 }
        users.forEach { userId ->
            runCatching { resume(userId, trigger = "quota_reset") }
                .onFailure { log.warn("Quota-reset resume failed userId={}: {}", userId, it.message) }
        }
        return users.size
    }

    private suspend fun resume(userId: String, trigger: String) {
        // Lift the hold first — even with nothing queued, future webhooks must flow again.
        // (This is also what unblocks the pipeline's accessBlockReason pre-check.)
        billingRepository.setLimitHitAt(userId, null)

        val blocked = runCatching { blockedRunRepository.listForOwner(userId) }
            .getOrElse { log.warn("Blocked-run lookup failed userId={}: {}", userId, it.message); emptyList() }
        if (blocked.isEmpty()) return

        val githubToken = runCatching { userRepository.findById(userId)?.githubToken }.getOrNull()
        var projectsResumed = 0
        var stringsResumed = 0
        for (run in blocked) {
            // Resume at the branch HEAD so pushes made while blocked are included; the
            // stored commit is the fallback when GitHub is unreachable or auth lapsed.
            val commit = githubToken?.let { token ->
                runCatching { githubService.getLatestCommitHash(run.repo, run.branch, token) }.getOrNull()
            } ?: run.commitHash
            runCatching {
                jobQueue.enqueueJob(WebhookPayload(
                    repositoryFullName = run.repo,
                    commitHash = commit,
                    branchName = run.branch,
                    projectId = run.projectId,
                    retriedFromRunId = run.originalRunId,
                    triggeredByUserId = userId
                ))
                blockedRunRepository.delete(run.projectId)
                projectsResumed++
                stringsResumed += run.stringsPending
            }.onFailure {
                log.warn("Resume enqueue failed project={} userId={}: {}", run.projectId, userId, it.message)
            }
        }
        if (projectsResumed == 0) return

        log.info("Quota resume: userId={} trigger={} projects={} strings≈{}",
            userId, trigger, projectsResumed, stringsResumed)
        userActivityService?.let {
            runCatching {
                it.record(userId, UserEvent.QUOTA_RESUMED, mapOf(
                    "trigger" to trigger,
                    "projects" to projectsResumed.toString(),
                    "strings" to stringsResumed.toString()
                ))
            }
        }
        inAppNotificationService?.notifyTranslationsResumed(userId, projectsResumed, stringsResumed, trigger)
    }
}
