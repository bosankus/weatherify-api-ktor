package com.transloom.services

import com.transloom.domain.UserEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Background loop that periodically scans the user base for actionable lifecycle
 * signals — stuck onboarding, expiring plans, abandoned payment sessions — and
 * logs them at WARN level. The output is structured so a future shipper (Slack
 * webhook, email queue, GrowthBook flag) can be bolted on without touching the
 * detection logic here.
 *
 * Why log-only for now: the platform has no email/Slack integration yet. Logging
 * gives ops the same insight today without committing to a delivery channel.
 * Once those exist, replace the log calls in [scan] with notifier hooks.
 */
class UserLifecycleMonitor(
    private val userActivityService: UserActivityService,
    private val interval: Duration = 6.hours,
    private val initialDelay: Duration = 1.minutes
) {
    private val log = LoggerFactory.getLogger(UserLifecycleMonitor::class.java)
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + supervisor)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            delay(initialDelay)
            while (isActive) {
                runCatching { scan() }
                    .onFailure { log.error("Lifecycle monitor scan failed: {}", it.message, it) }
                delay(interval)
            }
        }
        log.info("UserLifecycleMonitor started: interval={} initialDelay={}", interval, initialDelay)
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
        log.info("UserLifecycleMonitor stopped")
    }

    suspend fun scan() {
        val stuck = userActivityService.findStuckUsers()
        if (stuck.isNotEmpty()) {
            log.warn("Lifecycle scan: {} stuck user(s) detected", stuck.size)
            stuck.forEach { (user, reason) ->
                log.warn("  · userId={} login={} email={} reason=\"{}\"",
                    user.id, user.githubUsername, user.email ?: "—", reason)
            }
        }

        val expiring = userActivityService.findExpiringPlans()
        if (expiring.isNotEmpty()) {
            log.warn("Lifecycle scan: {} plan(s) expiring soon", expiring.size)
            expiring.forEach { (user, daysLeft, plan) ->
                log.warn("  · userId={} login={} plan={} daysLeft={}",
                    user.id, user.githubUsername, plan.name, daysLeft)
                // Record so the user's dashboard insights can pick it up next request.
                runCatching {
                    userActivityService.record(
                        user.id, UserEvent.PLAN_EXPIRY_NOTIFIED,
                        mapOf("plan" to plan.name, "daysLeft" to daysLeft.toString())
                    )
                }
            }
        }

        val abandoned = userActivityService.findAbandonedPayments()
        if (abandoned.isNotEmpty()) {
            log.warn("Lifecycle scan: {} payment session(s) abandoned > 1h", abandoned.size)
            abandoned.forEach { user ->
                log.warn("  · userId={} login={} email={}",
                    user.id, user.githubUsername, user.email ?: "—")
            }
        }

        if (stuck.isEmpty() && expiring.isEmpty() && abandoned.isEmpty()) {
            log.info("Lifecycle scan: no lifecycle issues detected")
        }
    }
}
