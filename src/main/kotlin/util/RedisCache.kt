package util

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import bose.ankush.util.getSecretValue

/**
 * Shared Redis cache wrapper backed by Lettuce.
 *
 * Connection is established lazily on first use. If Redis is unavailable
 * (e.g. local dev without a Redis instance), all operations silently
 * return null / no-op so the callers fall back to their live data sources.
 *
 * Redis URL is fetched from (in order of priority):
 *   1. REDIS_URL environment variable
 *   2. GCP Secret Manager (if GCP_PROJECT_ID is set)
 *   3. Local fallback (disabled by default for local dev)
 */
class RedisCache {
    private val logger = LoggerFactory.getLogger(RedisCache::class.java)

    private val redisClient: RedisClient? by lazy {
        val url = getSecretValue("redis-url")
        if (url.isBlank()) {
            logger.info("REDIS_URL not set — Redis caching disabled, falling back to live queries")
            null
        } else {
            try {
                RedisClient.create(RedisURI.create(url)).also {
                    logger.info("Redis client initialised for: $url")
                }
            } catch (e: Exception) {
                logger.warn("Failed to create Redis client ({}): {}. Caching disabled.", url, e.message)
                null
            }
        }
    }

    private val connection: StatefulRedisConnection<String, String>? by lazy {
        try {
            redisClient?.connect()
        } catch (e: Exception) {
            logger.warn("Failed to connect to Redis: {}. Caching disabled.", e.message)
            null
        }
    }

    private val commands: RedisAsyncCommands<String, String>?
        get() = connection?.async()

    /** Returns the cached value for [key], or null on miss / error / disabled. */
    suspend fun get(key: String): String? {
        return try {
            commands?.get(key)?.await()
        } catch (e: Exception) {
            logger.warn("Redis GET failed for key '{}': {}", key, e.message)
            null
        }
    }

    /** Stores [value] under [key] with [ttlSeconds] expiry. No-op if Redis is disabled. */
    suspend fun set(key: String, value: String, ttlSeconds: Long) {
        try {
            commands?.setex(key, ttlSeconds, value)?.await()
        } catch (e: Exception) {
            logger.warn("Redis SET failed for key '{}': {}", key, e.message)
        }
    }

    /**
     * Deletes all keys matching the given prefix pattern (e.g. "analytics:*").
     * Uses SCAN to avoid blocking the server — safe on large key spaces.
     */
    suspend fun invalidateByPrefix(prefix: String) {
        try {
            val cmds = commands ?: return
            var cursor = "0"
            do {
                val scanResult = cmds.scan(
                    io.lettuce.core.ScanCursor.of(cursor),
                    io.lettuce.core.ScanArgs.Builder.matches("$prefix*").limit(100)
                ).await()
                cursor = scanResult.cursor
                if (scanResult.keys.isNotEmpty()) {
                    cmds.del(*scanResult.keys.toTypedArray()).await()
                    logger.debug("Invalidated {} Redis keys with prefix '{}'", scanResult.keys.size, prefix)
                }
            } while (!scanResult.isFinished)
        } catch (e: Exception) {
            logger.warn("Redis invalidation failed for prefix '{}': {}", prefix, e.message)
        }
    }

    /** Closes the connection and shuts down the client. Call during application shutdown. */
    fun close() {
        try {
            connection?.close()
            redisClient?.shutdown()
        } catch (e: Exception) {
            logger.warn("Error closing Redis client: {}", e.message)
        }
    }
}
