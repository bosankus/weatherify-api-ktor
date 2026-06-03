package com.syncling.queue

import com.androidplay.core.common.QueueConnectionException
import com.androidplay.core.queue.JobQueueRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WebhookPayload(
    val repositoryFullName: String,
    val commitHash: String,
    val branchName: String,
    val projectId: String,
    val retriedFromRunId: String? = null,
    // Identity of the actor that caused the enqueue. For manual sync / retry the
    // routes set [triggeredByUserId] to the JWT user. For GitHub webhooks only
    // [triggeredByEmail] is known at receipt time; the worker resolves it to a
    // userId against project_members before invoking the pipeline.
    val triggeredByUserId: String? = null,
    val triggeredByEmail: String? = null,
    val forceTranslate: Boolean = false,
    // Idempotency key — prevents double-queueing when GitHub retries a delivery.
    val jobId: String = java.util.UUID.randomUUID().toString(),
    // Retry tracking — worker increments this and re-enqueues on transient failures.
    val attemptCount: Int = 0,
    val maxAttempts: Int = 3
)

private const val QUEUE_KEY = "transloom:jobs"
private const val DLQ_KEY = "transloom:dlq"
// Hard cap on the in-memory fallback queue. When Redis is unavailable and this fills,
// new webhooks are rejected rather than crashing the JVM with an OOM.
private const val FALLBACK_CAPACITY = 512
// If a job is in-flight for longer than this without completing, the lock is stale and
// cleared automatically so future webhooks for that project are not silently dropped.
private const val IN_FLIGHT_TIMEOUT_MS = 5 * 60_000L   // 5 minutes
private const val IN_FLIGHT_TTL_SECONDS = IN_FLIGHT_TIMEOUT_MS / 1000  // 300 s
private const val IN_FLIGHT_KEY_PREFIX = "tl:inflight:"

class TranslationJobQueue(private val jobQueue: JobQueueRepository) {
    private val log = LoggerFactory.getLogger(TranslationJobQueue::class.java)

    private val fallbackChannel = Channel<WebhookPayload>(FALLBACK_CAPACITY)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Maps projectId → enqueue timestamp. Prevents back-to-back webhooks from stacking
    // redundant jobs. Entries older than IN_FLIGHT_TIMEOUT_MS are evicted on every enqueue
    // so a crashed processor can never permanently block a project.
    private val inFlightProjects = ConcurrentHashMap<String, Long>()

    // Separate map for in-memory webhook rate-limiting (10s TTL). Kept apart from
    // inFlightProjects (5-min TTL) so the short-TTL cleanup in isWebhookRateLimited
    // never prematurely evicts in-flight project locks.
    private val rateLimitMap = ConcurrentHashMap<String, Long>()

    suspend fun enqueueJob(payload: WebhookPayload) {
        val now = System.currentTimeMillis()
        val projectId = payload.projectId

        // Idempotency: skip if this exact job was already accepted (protects against GitHub retries).
        if (payload.attemptCount == 0 && jobQueue.isConnected()) {
            try {
                if (!jobQueue.setNxExpire("tl:jobid:${payload.jobId}", 3600)) {
                    log.info("Idempotent skip: jobId={} already queued", payload.jobId)
                    return
                }
            } catch (e: Exception) {
                // Redis unavailable — allow through; commit dedup is still active
            }
        }

        if (isAlreadyInFlight(projectId, now)) {
            log.info("Dedup: project {} already in-flight, skipping commit={}", projectId, payload.commitHash.take(7))
            return
        }

        val json = Json.encodeToString(payload)
        if (jobQueue.isConnected()) {
            try {
                jobQueue.enqueue(QUEUE_KEY, json)
                log.info("Enqueued to Redis: repo={} commit={} attempt={}", payload.repositoryFullName, payload.commitHash.take(7), payload.attemptCount)
                return
            } catch (e: QueueConnectionException) {
                log.error("Failed to enqueue to Redis, falling back to memory: {}", e.message)
                // isAlreadyInFlight set the key in Redis; the in-memory worker that processes
                // the fallback job can't see it and won't delete it. Clear it now so the TTL
                // doesn't orphan the lock for 300 s after Redis recovers.
                runCatching { jobQueue.deleteKey("$IN_FLIGHT_KEY_PREFIX$projectId") }
            }
        }
        val queued = fallbackChannel.trySend(payload).isSuccess
        if (queued) {
            log.info("Enqueued to memory: repo={} commit={}", payload.repositoryFullName, payload.commitHash.take(7))
        } else {
            releaseInFlight(projectId)
            log.error("In-memory fallback queue full ({}) — dropped webhook for repo={} commit={}",
                FALLBACK_CAPACITY, payload.repositoryFullName, payload.commitHash.take(7))
        }
    }

    /**
     * Returns true if the project is already in-flight (caller should skip enqueueing).
     * Uses Redis SETNX for distributed dedup when Redis is available, falls back to the
     * in-process map otherwise. This prevents cross-instance duplicate runs: without
     * Redis-backed tracking, instance A marks a project in-flight but instance B dequeues
     * and processes the job, leaving A's map stale and silently dropping all subsequent
     * webhooks for that project for up to IN_FLIGHT_TIMEOUT_MS.
     */
    private suspend fun isAlreadyInFlight(projectId: String, now: Long): Boolean {
        return if (jobQueue.isConnected()) {
            try {
                !jobQueue.setNxExpire("$IN_FLIGHT_KEY_PREFIX$projectId", IN_FLIGHT_TTL_SECONDS)
            } catch (e: Exception) {
                // Redis call itself failed — fall back to in-memory guard
                inFlightProjects.entries.removeIf { now - it.value > IN_FLIGHT_TIMEOUT_MS }
                inFlightProjects.putIfAbsent(projectId, now) != null
            }
        } else {
            inFlightProjects.entries.removeIf { now - it.value > IN_FLIGHT_TIMEOUT_MS }
            inFlightProjects.putIfAbsent(projectId, now) != null
        }
    }

    private suspend fun releaseInFlight(projectId: String) {
        inFlightProjects.remove(projectId)
        // Always attempt the Redis delete regardless of isConnected() state. If Redis was
        // briefly unavailable when isAlreadyInFlight set the key but the job was processed
        // via the in-memory fallback, the stale key must be cleared when Redis recovers —
        // otherwise it blocks every subsequent push for this project until the TTL fires.
        runCatching { jobQueue.deleteKey("$IN_FLIGHT_KEY_PREFIX$projectId") }
    }

    /**
     * Returns true if this repo was recently seen (within 10s) and should be rate-limited.
     * Uses Redis SETNX for distributed enforcement; falls back to in-memory map.
     */
    suspend fun isWebhookRateLimited(repo: String): Boolean {
        val key = "tl:ratelimit:webhook:${repo.replace("/", ":")}"
        return if (jobQueue.isConnected()) {
            try {
                !jobQueue.setNxExpire(key, 10)
            } catch (e: Exception) {
                // Redis unavailable — in-memory fallback
                val now = System.currentTimeMillis()
                rateLimitMap.entries.removeIf { now - it.value > 10_000 }
                rateLimitMap.putIfAbsent(repo, now) != null
            }
        } else {
            val now = System.currentTimeMillis()
            rateLimitMap.entries.removeIf { now - it.value > 10_000 }
            rateLimitMap.putIfAbsent(repo, now) != null
        }
    }

    /**
     * Returns true if this GitHub delivery ID was already processed (within 10 minutes).
     * Prevents GitHub's own webhook retry from double-queueing the same delivery.
     */
    suspend fun isDeliveryAlreadySeen(deliveryId: String): Boolean {
        val key = "tl:delivery:$deliveryId"
        return if (jobQueue.isConnected()) {
            try {
                !jobQueue.setNxExpire(key, 600)
            } catch (e: Exception) {
                false  // Redis unavailable — allow through; commit dedup is still active
            }
        } else {
            false  // No in-memory fallback needed; commit dedup covers this
        }
    }

    fun startWorker(processor: suspend (WebhookPayload) -> Unit) {
        val retryingProcessor: suspend (WebhookPayload) -> Unit = { payload ->
            try {
                processor(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleJobFailure(payload, e)
            } finally {
                releaseInFlight(payload.projectId)
            }
        }
        scope.launch {
            log.info("Worker started (backend: {})", if (jobQueue.isConnected()) "redis" else "in-memory")
            if (jobQueue.isConnected()) {
                startRedisWorker(retryingProcessor)
            } else {
                startInMemoryWorker(retryingProcessor)
            }
        }
    }

    private suspend fun handleJobFailure(payload: WebhookPayload, error: Exception) {
        log.error("Job failed for repo={} commit={} attempt={}/{}: {}",
            payload.repositoryFullName, payload.commitHash.take(7),
            payload.attemptCount + 1, payload.maxAttempts, error.message)

        if (payload.attemptCount + 1 < payload.maxAttempts) {
            val backoffSeconds = minOf(30L, 1L shl (payload.attemptCount + 1))
            log.info("Scheduling retry for repo={} in {}s (attempt {}/{})",
                payload.repositoryFullName, backoffSeconds, payload.attemptCount + 1, payload.maxAttempts)
            delay(backoffSeconds * 1_000)
            val retryPayload = payload.copy(
                attemptCount = payload.attemptCount + 1,
                // Release the in-flight lock before re-enqueue so the new attempt can acquire it
            )
            releaseInFlight(payload.projectId)
            enqueueJob(retryPayload)
        } else {
            log.error("Job exhausted all {} attempts — sending to DLQ: repo={} commit={}",
                payload.maxAttempts, payload.repositoryFullName, payload.commitHash.take(7))
            sendToDlq(payload, error.message ?: "unknown error")
        }
    }

    private suspend fun sendToDlq(payload: WebhookPayload, errorMsg: String) {
        if (!jobQueue.isConnected()) {
            log.error("DLQ unavailable (Redis disconnected) — job permanently lost: repo={} commit={}",
                payload.repositoryFullName, payload.commitHash.take(7))
            return
        }
        try {
            val dlqEntry = buildString {
                append("{")
                append("\"timestamp\":${System.currentTimeMillis()},")
                append("\"error\":${kotlinx.serialization.json.Json.encodeToString(errorMsg)},")
                append("\"payload\":${Json.encodeToString(payload)}")
                append("}")
            }
            jobQueue.enqueue(DLQ_KEY, dlqEntry)
            log.info("Job sent to DLQ: repo={} commit={}", payload.repositoryFullName, payload.commitHash.take(7))
        } catch (e: Exception) {
            log.error("Failed to write to DLQ: {}", e.message)
        }
    }

    private suspend fun startRedisWorker(processor: suspend (WebhookPayload) -> Unit) {
        while (currentCoroutineContext().isActive) {
            if (!jobQueue.isConnected()) {
                log.warn("Redis unavailable — switching worker to in-memory queue")
                startInMemoryWorker(processor)
                return
            }
            try {
                val json = jobQueue.blockingDequeue(QUEUE_KEY, 30.0) ?: continue
                try {
                    val payload = Json.decodeFromString<WebhookPayload>(json)
                    log.info("Dequeued: repo={} commit={} attempt={}", payload.repositoryFullName, payload.commitHash.take(7), payload.attemptCount)
                    processor(payload)
                } catch (e: Exception) {
                    log.error("Failed to process job: {}", e.message, e)
                }
            } catch (e: QueueConnectionException) {
                log.error("Redis connection lost: {}. Attempting reconnect in 5s.", e.message)
                delay(5_000)
                try {
                    jobQueue.connect()
                    log.info("Redis reconnected — resuming Redis worker")
                } catch (e2: QueueConnectionException) {
                    log.warn("Redis reconnect failed — switching to in-memory queue. Jobs in Redis may be delayed until Redis recovers.")
                    startInMemoryWorker(processor)
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Queue worker error: {}", e.message)
                delay(1_000)
            }
        }
    }

    private suspend fun startInMemoryWorker(processor: suspend (WebhookPayload) -> Unit) {
        while (currentCoroutineContext().isActive) {
            // Periodically check if Redis has recovered so jobs pushed to Redis during
            // the outage period become visible again.
            if (jobQueue.isConnected()) {
                log.info("Redis recovered — switching worker back to Redis")
                startRedisWorker(processor)
                return
            }
            val payload = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                fallbackChannel.receiveCatching().getOrNull()
            } ?: continue
            processor(payload)
        }
    }

    fun close() {
        scope.cancel()
        fallbackChannel.close()
        jobQueue.close()
    }
}
