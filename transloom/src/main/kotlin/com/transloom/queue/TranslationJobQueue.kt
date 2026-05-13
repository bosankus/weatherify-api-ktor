package com.transloom.queue

import com.androidplay.core.common.QueueConnectionException
import com.androidplay.core.queue.JobQueueRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class WebhookPayload(
    val repositoryFullName: String,
    val commitHash: String,
    val branchName: String,
    val projectId: String
)

private const val QUEUE_KEY = "transloom:jobs"

class TranslationJobQueue(private val jobQueue: JobQueueRepository) {
    private val log = LoggerFactory.getLogger(TranslationJobQueue::class.java)

    private val fallbackChannel = Channel<WebhookPayload>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun enqueueJob(payload: WebhookPayload) {
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
        fallbackChannel.send(payload)
        log.info("Enqueued to memory: repo={} commit={}", payload.repositoryFullName, payload.commitHash.take(7))
    }

    fun startWorker(processor: suspend (WebhookPayload) -> Unit) {
        scope.launch {
            log.info("Worker started (backend: {})", if (jobQueue.isConnected()) "redis" else "in-memory")
            if (jobQueue.isConnected()) {
                startRedisWorker(processor)
            } else {
                startInMemoryWorker(processor)
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
