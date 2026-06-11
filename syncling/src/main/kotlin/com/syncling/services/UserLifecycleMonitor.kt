package com.syncling.services

import com.syncling.domain.UserEvent
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

/** Periodically scans for stuck onboarding, expiring plans, and abandoned payment sessions. Logs at WARN and creates in-app notifications + optional transactional emails. */
class UserLifecycleMonitor(
    private val userActivityService: UserActivityService,
    private val notificationService: NotificationService? = null,
    private val inAppNotificationService: InAppNotificationService? = null,
    private val razorpayService: RazorpayBillingService? = null,
    private val interval: Duration = 1.hours,     // was 6h — users stuck > 1h are now caught faster
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
        // Reconcile overdue paid subscriptions against the Razorpay API first so users whose
        // payment-failed/cancelled webhooks were missed are blocked or downgraded promptly.
        razorpayService?.let {
            runCatching { it.reconcileOverdueSubscriptions() }
                .onFailure { e -> log.error("Subscription reconciliation failed: {}", e.message, e) }
        }

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
                        inAppNotificationService?.notifyOnboarding(user.id, reason)
                        notificationService?.sendOnboardingReminder(user, reason)
                    }
                }.onFailure { log.warn("ONBOARDING_STUCK notification failed userId={}: {}", user.id, it.message) }
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
                        inAppNotificationService?.notifyPlanExpiry(user.id, daysLeft, plan)
                        notificationService?.sendPlanExpiryWarning(user, daysLeft, plan)
                    }
                }.onFailure { log.warn("PLAN_EXPIRY_NOTIFIED notification failed userId={}: {}", user.id, it.message) }
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
                        // Look up pending plan from billing repository via userActivityService
                        val pendingPlan = userActivityService.getPendingPlan(user.id)
                        if (pendingPlan != null) {
                            inAppNotificationService?.notifyCheckoutAbandoned(user.id, pendingPlan)
                            notificationService?.sendCheckoutAbandoned(user, pendingPlan)
                        }
                    }
                }.onFailure { log.warn("CHECKOUT_ABANDONED notification failed userId={}: {}", user.id, it.message) }
            }
        }

        if (stuck.isEmpty() && expiring.isEmpty() && abandoned.isEmpty()) {
            log.info("Lifecycle scan: no lifecycle issues detected")
        }
    }
}
