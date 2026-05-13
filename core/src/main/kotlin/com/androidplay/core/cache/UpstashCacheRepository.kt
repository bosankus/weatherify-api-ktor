package com.androidplay.core.cache

import com.androidplay.core.cache.CacheRepository
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
 * CacheRepository backed by Upstash Redis REST API.
 * Uses Java 17's built-in HttpClient — no additional library required.
 * All operations silently no-op/return null if URL is blank (local dev).
 */
class UpstashCacheRepository(
    private val redisUrl: String
) : CacheRepository {

    private val log = LoggerFactory.getLogger(UpstashCacheRepository::class.java)
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val enabled = redisUrl.isNotBlank()

    // Sends a single Redis command as a JSON array to the Upstash REST endpoint.
    // Returns the "result" field of the response, or null on error / disabled.
    private suspend fun cmd(requestTimeoutSeconds: Long = 10, vararg args: String): JsonElement? {
        if (!enabled) return null
        return try {
            val body = json.encodeToString(args.toList())
            val request = HttpRequest.newBuilder()
                .uri(URI.create(redisUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = withContext(Dispatchers.IO) {
                http.send(request, HttpResponse.BodyHandlers.ofString())
            }
            json.parseToJsonElement(response.body()).jsonObject["result"]
        } catch (e: Exception) {
            log.warn("Upstash {} failed: {}", args.firstOrNull(), e.message)
            null
        }
    }

    override suspend fun get(key: String): String? =
        cmd(10, "GET", key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

    override suspend fun set(key: String, value: String, ttlSeconds: Long) {
        cmd(10, "SETEX", key, ttlSeconds.toString(), value)
    }

    override suspend fun delete(key: String) {
        cmd(10, "DEL", key)
    }

    override suspend fun invalidateByPrefix(prefix: String) {
        if (!enabled) return
        var cursor = "0"
        do {
            val result = cmd(10, "SCAN", cursor, "MATCH", "$prefix*", "COUNT", "100")
                ?.takeIf { it !is JsonNull }?.jsonArray ?: break
            cursor = result[0].jsonPrimitive.content
            val keys = result[1].jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
            if (keys.isNotEmpty()) {
                val delArgs = buildList { add("DEL"); addAll(keys) }.toTypedArray()
                cmd(10, *delArgs)
                log.debug("Invalidated {} keys with prefix '{}'", keys.size, prefix)
            }
        } while (cursor != "0")
    }

    override fun close() {} // stateless HTTP — nothing to close
}
