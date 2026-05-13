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

private val log = LoggerFactory.getLogger("CoreInfraModule")

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
    single<CacheRepository> { UpstashCacheRepository(redisUrl) }
    single<JobQueueRepository> {
        UpstashJobQueueRepository(redisUrl).also {
            try { it.connect() } catch (e: QueueConnectionException) {
                log.warn("Upstash queue unavailable: {}. Falling back.", e.message)
            }
        }
    }
}
