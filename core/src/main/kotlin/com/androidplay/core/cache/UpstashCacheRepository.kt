package com.androidplay.core.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.ScanParams

class UpstashCacheRepository(
    private val pool: JedisPool?
) : CacheRepository {

    private val log = LoggerFactory.getLogger(UpstashCacheRepository::class.java)
    private val enabled = pool != null

    override suspend fun get(key: String): String? {
        if (!enabled) return null
        return try {
            withContext(Dispatchers.IO) { pool!!.resource.use { it.get(key) } }
        } catch (e: Exception) {
            log.warn("Redis GET failed for key={}: {}", key, e.message)
            null
        }
    }

    override suspend fun set(key: String, value: String, ttlSeconds: Long) {
        if (!enabled) return
        try {
            withContext(Dispatchers.IO) { pool!!.resource.use { it.setex(key, ttlSeconds, value) } }
        } catch (e: Exception) {
            log.warn("Redis SETEX failed for key={}: {}", key, e.message)
        }
    }

    override suspend fun delete(key: String) {
        if (!enabled) return
        try {
            withContext(Dispatchers.IO) { pool!!.resource.use { it.del(key) } }
        } catch (e: Exception) {
            log.warn("Redis DEL failed for key={}: {}", key, e.message)
        }
    }

    override suspend fun invalidateByPrefix(prefix: String) {
        if (!enabled) return
        try {
            withContext(Dispatchers.IO) {
                pool!!.resource.use { jedis ->
                    val params = ScanParams().match("$prefix*").count(100)
                    var cursor = ScanParams.SCAN_POINTER_START
                    do {
                        val result = jedis.scan(cursor, params)
                        cursor = result.cursor
                        val keys = result.result
                        if (keys.isNotEmpty()) {
                            jedis.del(*keys.toTypedArray())
                            log.debug("Invalidated {} keys with prefix '{}'", keys.size, prefix)
                        }
                    } while (cursor != ScanParams.SCAN_POINTER_START)
                }
            }
        } catch (e: Exception) {
            log.warn("Redis SCAN/DEL failed for prefix={}: {}", prefix, e.message)
        }
    }

    override fun close() {
        if (enabled && !pool!!.isClosed) pool.close()
    }
}
