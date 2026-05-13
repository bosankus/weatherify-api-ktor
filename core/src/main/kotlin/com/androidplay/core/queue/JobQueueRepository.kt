package com.androidplay.core.queue

interface JobQueueRepository {
    fun connect()
    fun isConnected(): Boolean
    suspend fun enqueue(key: String, value: String)
    suspend fun blockingDequeue(key: String, timeoutSeconds: Double): String?
    fun close()
}
