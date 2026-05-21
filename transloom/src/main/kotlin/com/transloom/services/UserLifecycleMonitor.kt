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
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Periodically scans for stuck onboarding, expiring plans, and abandoned payment sessions. Logs at WARN; plug in a notifier to [scan] when email/Slack is ready. */
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
                runCatching {
                    if (userActivityService.shouldNotify(user.id, setOf(UserEvent.ONBOARDING_STUCK), withinHours = 24)) {
                        userActivityService.record(
                            user.id, UserEvent.ONBOARDING_STUCK,
                            mapOf("reason" to reason, "step" to user.onboardingStep.name)
                        )
                    }
                }.onFailure { log.warn("ONBOARDING_STUCK record failed userId={}: {}", user.id, it.message) }
            }
        }

        val expiring = userActivityService.findExpiringPlans()
        if (expiring.isNotEmpty()) {
            log.warn("Lifecycle scan: {} plan(s) expiring soon", expiring.size)
            expiring.forEach { (user, daysLeft, plan) ->
                log.warn("  · userId={} login={} plan={} daysLeft={}",
                    user.id, user.githubUsername, plan.name, daysLeft)
                runCatching {
                    if (userActivityService.shouldNotify(user.id, setOf(UserEvent.PLAN_EXPIRY_NOTIFIED), withinHours = 24)) {
                        userActivityService.record(
                            user.id, UserEvent.PLAN_EXPIRY_NOTIFIED,
                            mapOf("plan" to plan.name, "daysLeft" to daysLeft.toString())
                        )
                    }
                }.onFailure { log.warn("PLAN_EXPIRY_NOTIFIED record failed userId={}: {}", user.id, it.message) }
            }
        }

        val abandoned = userActivityService.findAbandonedPayments()
        if (abandoned.isNotEmpty()) {
            log.warn("Lifecycle scan: {} payment session(s) abandoned > 1h", abandoned.size)
            abandoned.forEach { user ->
                log.warn("  · userId={} login={} email={}",
                    user.id, user.githubUsername, user.email ?: "—")
                runCatching {
                    if (userActivityService.shouldNotify(user.id, setOf(UserEvent.CHECKOUT_ABANDONED), withinHours = 24)) {
                        userActivityService.record(
                            user.id, UserEvent.CHECKOUT_ABANDONED,
                            mapOf("detectedAt" to Clock.System.now().toString())
                        )
                    }
                }.onFailure { log.warn("CHECKOUT_ABANDONED record failed userId={}: {}", user.id, it.message) }
            }
        }

        if (stuck.isEmpty() && expiring.isEmpty() && abandoned.isEmpty()) {
            log.info("Lifecycle scan: no lifecycle issues detected")
        }
    }
}
