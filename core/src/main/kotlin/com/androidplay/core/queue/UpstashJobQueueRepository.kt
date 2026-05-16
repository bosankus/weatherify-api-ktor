package com.androidplay.core.queue

import com.androidplay.core.common.QueueConnectionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool

/**
 * JobQueueRepository backed by a direct TCP Redis connection (rediss:// for TLS).
 * Shares the JedisPool with UpstashCacheRepository — one pool per process.
 */
class UpstashJobQueueRepository(
    private val pool: JedisPool?
) : JobQueueRepository {

    private val log = LoggerFactory.getLogger(UpstashJobQueueRepository::class.java)

    @Volatile private var connected = false

    override fun connect() {
        if (pool == null) throw QueueConnectionException("Redis URL not configured")
        try {
            pool.resource.use { it.ping() }
            connected = true
            log.info("Redis TCP job queue connected")
        } catch (e: Exception) {
            throw QueueConnectionException("Redis connect failed: ${e.message}", e)
        }
    }

    override fun isConnected(): Boolean = connected && pool != null

    override suspend fun enqueue(key: String, value: String) {
        try {
            withContext(Dispatchers.IO) { pool!!.resource.use { it.lpush(key, value) } }
        } catch (e: Exception) {
            throw QueueConnectionException("LPUSH failed: ${e.message}", e)
        }
    }

    // BRPOP blocks server-side for up to timeoutSeconds then returns null if no item arrives.
    // The Jedis connection is held for the full blocking duration — this is expected.
    override suspend fun blockingDequeue(key: String, timeoutSeconds: Double): String? {
        return try {
            withContext(Dispatchers.IO) {
                pool!!.resource.use { it.brpop(timeoutSeconds, key)?.value }
            }
        } catch (e: Exception) {
            throw QueueConnectionException("BRPOP failed: ${e.message}", e)
        }
    }

    override fun close() {
        connected = false
        // Pool lifecycle is owned by CoreInfraModule — closed via CacheRepository.close()
    }
}
