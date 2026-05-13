package com.androidplay.core.queue

import com.androidplay.core.common.QueueConnectionException
import com.androidplay.core.queue.JobQueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * JobQueueRepository backed by Upstash Redis REST API (LPUSH/BRPOP).
 * "connect()" is a no-op validation check — REST is stateless, no persistent connection held.
 */
class UpstashJobQueueRepository(
    private val redisUrl: String
) : JobQueueRepository {

    private val log = LoggerFactory.getLogger(UpstashJobQueueRepository::class.java)
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var connected = false

    override fun connect() {
        if (redisUrl.isBlank()) {
            throw QueueConnectionException("Redis URL not configured")
        }
        connected = true
        log.info("Upstash job queue configured at {}", redisUrl)
    }

    override fun isConnected(): Boolean = connected && redisUrl.isNotBlank()

    // requestTimeoutSeconds must exceed the BRPOP timeout to avoid premature HTTP timeout.
    private suspend fun cmd(requestTimeoutSeconds: Long, vararg args: String): JsonElement? {
        return try {
            val body = json.encodeToString(args.toList())
            val request = HttpRequest.newBuilder()
                .uri(URI.create(redisUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            withContext(Dispatchers.IO) {
                http.send(request, HttpResponse.BodyHandlers.ofString())
            }.body()
                .let { json.parseToJsonElement(it).jsonObject["result"] }
        } catch (e: Exception) {
            throw QueueConnectionException("Upstash ${args.firstOrNull()} failed: ${e.message}", e)
        }
    }

    override suspend fun enqueue(key: String, value: String) {
        cmd(10, "LPUSH", key, value)
    }

    // BRPOP blocks server-side for up to timeoutSeconds, then returns null.
    // HTTP timeout is set to timeoutSeconds + 10 to avoid cutting the connection early.
    override suspend fun blockingDequeue(key: String, timeoutSeconds: Double): String? {
        val result = cmd(
            requestTimeoutSeconds = timeoutSeconds.toLong() + 10,
            "BRPOP", key, timeoutSeconds.toLong().toString()
        )
        return result?.takeIf { it !is JsonNull }
            ?.jsonArray?.getOrNull(1)?.jsonPrimitive?.contentOrNull
    }

    override fun close() {
        connected = false
    }
}
