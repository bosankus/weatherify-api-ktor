package com.transloom.queue

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
    val retriedFromRunId: String? = null
)

private const val QUEUE_KEY = "transloom:jobs"
// Hard cap on the in-memory fallback queue. When Redis is unavailable and this fills,
// new webhooks are rejected rather than crashing the JVM with an OOM.
private const val FALLBACK_CAPACITY = 512
// If a job is in-flight for longer than this without completing, the lock is stale and
// cleared automatically so future webhooks for that project are not silently dropped.
private const val IN_FLIGHT_TIMEOUT_MS = 5 * 60_000L   // 5 minutes

class TranslationJobQueue(private val jobQueue: JobQueueRepository) {
    private val log = LoggerFactory.getLogger(TranslationJobQueue::class.java)

    private val fallbackChannel = Channel<WebhookPayload>(FALLBACK_CAPACITY)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Maps projectId → enqueue timestamp. Prevents back-to-back webhooks from stacking
    // redundant jobs. Entries older than IN_FLIGHT_TIMEOUT_MS are evicted on every enqueue
    // so a crashed processor can never permanently block a project.
    private val inFlightProjects = ConcurrentHashMap<String, Long>()

    suspend fun enqueueJob(payload: WebhookPayload) {
        val now = System.currentTimeMillis()
        // Evict stale in-flight entries before checking — prevents permanent lock on crash
        inFlightProjects.entries.removeIf { now - it.value > IN_FLIGHT_TIMEOUT_MS }

        if (inFlightProjects.putIfAbsent(payload.projectId, now) != null) {
            log.info("Dedup: project {} already in-flight, skipping commit={}", payload.projectId, payload.commitHash.take(7))
            return
        }

        val json = Json.encodeToString(payload)
        if (jobQueue.isConnected()) {
            try {
                jobQueue.enqueue(QUEUE_KEY, json)
                log.info("Enqueued to Redis: repo={} commit={}", payload.repositoryFullName, payload.commitHash.take(7))
                return
            } catch (e: QueueConnectionException) {
                log.error("Failed to enqueue to Redis, falling back to memory: {}", e.message)
            }
        }
        val queued = fallbackChannel.trySend(payload).isSuccess
        if (queued) {
            log.info("Enqueued to memory: repo={} commit={}", payload.repositoryFullName, payload.commitHash.take(7))
        } else {
            inFlightProjects.remove(payload.projectId)
            log.error("In-memory fallback queue full ({}) — dropped webhook for repo={} commit={}",
                FALLBACK_CAPACITY, payload.repositoryFullName, payload.commitHash.take(7))
        }
    }

    fun startWorker(processor: suspend (WebhookPayload) -> Unit) {
        val tracked: suspend (WebhookPayload) -> Unit = { payload ->
            try { processor(payload) } finally { inFlightProjects.remove(payload.projectId) }
        }
        scope.launch {
            log.info("Worker started (backend: {})", if (jobQueue.isConnected()) "redis" else "in-memory")
            if (jobQueue.isConnected()) {
                startRedisWorker(tracked)
            } else {
                startInMemoryWorker(tracked)
            }
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
                    log.info("Dequeued: repo={} commit={}", payload.repositoryFullName, payload.commitHash.take(7))
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
        for (payload in fallbackChannel) {
            try {
                processor(payload)
            } catch (e: Exception) {
                log.error("Failed to process job for repo={}: {}", payload.repositoryFullName, e.message, e)
            }
        }
    }

    fun close() {
        scope.cancel()
        fallbackChannel.close()
        jobQueue.close()
    }
}
