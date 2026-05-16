package com.androidplay.core.di

import com.androidplay.core.cache.CacheRepository
import com.androidplay.core.cache.UpstashCacheRepository
import com.androidplay.core.common.QueueConnectionException
import com.androidplay.core.mongo.MongoConnection
import com.androidplay.core.queue.JobQueueRepository
import com.androidplay.core.queue.UpstashJobQueueRepository
import com.mongodb.MongoClientSettings
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.net.URI
import java.time.Duration

private val log = LoggerFactory.getLogger("CoreInfraModule")

private fun buildPool(redisUrl: String): JedisPool? {
    if (redisUrl.isBlank()) return null
    val config = JedisPoolConfig().apply {
        maxTotal = 10
        maxIdle = 5
        minIdle = 1
        setMaxWait(Duration.ofSeconds(5))
        testOnBorrow = true
    }
    return JedisPool(config, URI(redisUrl))
}

fun coreInfraModule(
    mongoUri: String,
    databaseName: String,
    redisUrl: String,
    mongoSettings: MongoClientSettings? = null,
) = module {
    single {
        mongoSettings
            ?.let { MongoConnection.connect(it, databaseName) }
            ?: MongoConnection.connect(mongoUri, databaseName)
    }

    // One shared pool for both cache and queue — avoids duplicate TCP connections.
    val pool = buildPool(redisUrl)

    single<CacheRepository> { UpstashCacheRepository(pool) }
    single<JobQueueRepository> {
        UpstashJobQueueRepository(pool).also {
            try { it.connect() } catch (e: QueueConnectionException) {
                log.warn("Redis queue unavailable: {}. Falling back to in-memory queue.", e.message)
            }
        }
    }
}
