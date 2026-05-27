package com.androidplay.core.queue

interface JobQueueRepository {
    fun connect()
    fun isConnected(): Boolean
    suspend fun enqueue(key: String, value: String)
    suspend fun blockingDequeue(key: String, timeoutSeconds: Double): String?
    /**
     * Atomically set [key] to "1" with [ttlSeconds] expiry only if the key does not exist.
     * Returns true if the key was newly set (this caller acquired the lock),
     * false if the key already existed (another holder owns it).
     */
    suspend fun setNxExpire(key: String, ttlSeconds: Long): Boolean
    /** Delete a key. No-op if the key does not exist. */
    suspend fun deleteKey(key: String)
    fun close()
}
