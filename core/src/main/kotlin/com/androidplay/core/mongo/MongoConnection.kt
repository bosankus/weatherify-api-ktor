package com.androidplay.core.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.util.concurrent.TimeUnit

object MongoConnection {
    fun connect(uri: String, databaseName: String): MongoDatabase =
        MongoClient.create(uri).getDatabase(databaseName)

    fun connect(settings: MongoClientSettings, databaseName: String): MongoDatabase =
        MongoClient.create(settings).getDatabase(databaseName)

    fun pooledSettings(
        uri: String,
        maxPoolSize: Int = 50,
        minPoolSize: Int = 5,
        maxWaitSeconds: Long = 5,
        connectTimeoutSeconds: Long = 5,
        readTimeoutSeconds: Long = 10,
    ): MongoClientSettings = MongoClientSettings.builder()
        .applyConnectionString(ConnectionString(uri))
        .applyToConnectionPoolSettings { it.maxSize(maxPoolSize).minSize(minPoolSize)
            .maxWaitTime(maxWaitSeconds, TimeUnit.SECONDS) }
        .applyToSocketSettings { it.connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS) }
        .build()
}
