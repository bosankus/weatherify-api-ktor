package util

import config.Environment
import domain.model.Result
import domain.service.SubscriptionService
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Background job for processing subscription expirations, grace period expiry, and notifications.
 *
 * This job runs three scheduled tasks:
 * - Expiration check: Runs based on configured interval (default: every 12 hours) to transition ACTIVE subscriptions to GRACE_PERIOD
 * - Grace period check: Runs once daily (every 24 hours) to transition GRACE_PERIOD subscriptions to EXPIRED
 * - Notification check: Runs once daily (every 24 hours) to send expiry warnings and notifications
 *
 * Requirements: 6.1, 6.2, 6.3, 6.4, 7.3, 7.4
 */
class SubscriptionExpirationJob(
    private val subscriptionService: SubscriptionService,
    private val notificationService: domain.service.SubscriptionNotificationService
) {
    private val logger = LoggerFactory.getLogger(SubscriptionExpirationJob::class.java)
    private val scheduler = Executors.newScheduledThreadPool(2)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // Run grace period check once daily (every 24 hours)
        private const val GRACE_PERIOD_CHECK_INTERVAL_HOURS = 24L

        // Initial delay before first run (1 minute to allow app startup)
        private const val INITIAL_DELAY_MINUTES = 1L
    }

    /**
     * Start the scheduled jobs.
     * This should be called during application startup.
     */
    fun start() {
        logger.info("Starting subscription expiration jobs")

        // Get expiration check interval from environment (default: 12 hours = 720 minutes)
        val expirationCheckIntervalMinutes = Environment.getSubscriptionExpiryCheckIntervalMinutes()

        // Schedule expiration check
        scheduler.scheduleAtFixedRate(
            { runExpirationCheck() },
            INITIAL_DELAY_MINUTES,
            expirationCheckIntervalMinutes,
            TimeUnit.MINUTES
        )
        logger.info("Scheduled expiration check to run every $expirationCheckIntervalMinutes minutes")

        // Schedule grace period check (once daily)
        scheduler.scheduleAtFixedRate(
            { runGracePeriodCheck() },
            INITIAL_DELAY_MINUTES,
            TimeUnit.HOURS.toMinutes(GRACE_PERIOD_CHECK_INTERVAL_HOURS),
            TimeUnit.MINUTES
        )
        logger.info("Scheduled grace period check to run every $GRACE_PERIOD_CHECK_INTERVAL_HOURS hours")

        // Schedule notification check (once daily)
        scheduler.scheduleAtFixedRate(
            { runNotificationCheck() },
            INITIAL_DELAY_MINUTES + 5, // Start 5 minutes after other jobs
            TimeUnit.HOURS.toMinutes(GRACE_PERIOD_CHECK_INTERVAL_HOURS),
            TimeUnit.MINUTES
        )
        logger.info("Scheduled notification check to run every $GRACE_PERIOD_CHECK_INTERVAL_HOURS hours")
    }

    /**
     * Stop the scheduled jobs.
     * This should be called during application shutdown.
     */
    fun stop() {
        logger.info("Stopping subscription expiration jobs")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
        scope.cancel()
        logger.info("Subscription expiration jobs stopped")
    }

    /**
     * Run the expiration check process.
     * Transitions ACTIVE subscriptions past their end date to GRACE_PERIOD status.
     */
    private fun runExpirationCheck() {
        scope.launch {
            try {
                logger.info("Running subscription expiration check")
                val result = subscriptionService.processExpiredSubscriptions()

                when (result) {
                    is Result.Success -> {
                        val count = result.data
                        logger.info("Expiration check completed successfully. Processed $count subscriptions")
                    }

                    is Result.Error -> {
                        logger.error("Expiration check failed: ${result.message}", result.exception)
                    }
                }
            } catch (e: Exception) {
                logger.error("Unexpected error during expiration check", e)
            }
        }
    }

    /**
     * Run the grace period expiry check process.
     * Transitions GRACE_PERIOD subscriptions past their grace period end to EXPIRED status.
     */
    private fun runGracePeriodCheck() {
        scope.launch {
            try {
                logger.info("Running grace period expiry check")
                val result = subscriptionService.processGracePeriodExpiry()

                when (result) {
                    is Result.Success -> {
                        val count = result.data
                        logger.info("Grace period check completed successfully. Processed $count subscriptions")
                    }

                    is Result.Error -> {
                        logger.error("Grace period check failed: ${result.message}", result.exception)
                    }
                }
            } catch (e: Exception) {
                logger.error("Unexpected error during grace period check", e)
            }
        }
    }

    /**
     * Run the notification check process.
     * Sends expiry warnings and notifications to users with subscriptions expiring soon.
     */
    private fun runNotificationCheck() {
        scope.launch {
            try {
                logger.info("Running subscription notification check")
                val result = notificationService.processExpiryNotifications()

                when (result) {
                    is Result.Success -> {
                        val count = result.data
                        logger.info("Notification check completed successfully. Sent $count notifications")
                    }

                    is Result.Error -> {
                        logger.error("Notification check failed: ${result.message}", result.exception)
                    }
                }
            } catch (e: Exception) {
                logger.error("Unexpected error during notification check", e)
            }
        }
    }
}
