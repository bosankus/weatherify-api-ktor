package com.androidplay.core.cache

interface CacheRepository {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String, ttlSeconds: Long)
    suspend fun delete(key: String)
    suspend fun invalidateByPrefix(prefix: String)
    fun close()
}
