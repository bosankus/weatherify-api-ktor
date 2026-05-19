package com.transloom.services

import com.transloom.domain.PipelineRunState
import com.transloom.domain.PipelineStepState
import com.transloom.domain.initialSteps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class PipelineEvent(
    val type: String,            // start | step | finish
    val runId: String,
    val stepId: String? = null,
    val status: String? = null,
    val detail: String? = null,
    val prUrl: String? = null,
    val error: String? = null,
    val snapshot: PipelineRunState? = null,
    val surfaceSkipped: Int? = null
)

/**
 * Broadcasts pipeline progress events via Redis pub/sub when a Redis URL is provided,
 * falling back to an in-memory SharedFlow for local dev / no-Redis environments.
 *
 * Redis keys:
 *   tl:events:{userId}  — pub/sub channel for live SSE delivery across instances
 *   tl:run:{runId}      — JSON of PipelineRunState (TTL 24 h)
 *   tl:runs:{userId}    — List of recent runIds, capped at 20 (TTL 24 h)
 */
class PipelineEventBus(private val redisUrl: String? = null) {

    private val log = LoggerFactory.getLogger(PipelineEventBus::class.java)
    private val json = Json { encodeDefaults = false }

    private val pool: JedisPool? = redisUrl?.takeIf { it.isNotBlank() }?.let { url ->
        runCatching {
            JedisPool(JedisPoolConfig().apply { maxTotal = 6; maxIdle = 3 }, URI(url))
                .also { p -> p.resource.use { j -> j.ping() } }
        }.onSuccess { log.info("PipelineEventBus: Redis pub/sub enabled") }
         .onFailure { log.warn("PipelineEventBus: Redis unavailable ({}), using in-memory fallback", it.message) }
         .getOrNull()
    }

    private val inRedis = pool != null

    // Dedicated scope for fire-and-forget Redis writes (store + publish)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Ephemeral in-memory step tracking for in-progress runs (single-instance, always)
    private val activeSteps = ConcurrentHashMap<String, MutableList<PipelineStepState>>()

    // In-memory fallback structures (used only when Redis is unavailable)
    private val memRuns = ConcurrentHashMap<String, ArrayDeque<PipelineRunState>>()
    private val memFlows = ConcurrentHashMap<String, MutableSharedFlow<String>>()

    companion object {
        private const val RUN_TTL = 86_400L  // 24 h in seconds
        private const val MAX_RUNS = 20L
        fun channelKey(userId: String) = "tl:events:$userId"
        fun runStateKey(runId: String) = "tl:run:$runId"
        fun runsListKey(userId: String) = "tl:runs:$userId"
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun eventsFor(userId: String): Flow<String> {
        if (!inRedis) return memFlowFor(userId).asSharedFlow()
        return callbackFlow {
            // Dedicated Jedis connection per SSE subscriber — subscribe() blocks the thread
            // indefinitely, so it must NOT come from the shared pool (would exhaust it).
            val jedis = Jedis(URI(redisUrl!!))
            val sub = object : JedisPubSub() {
                override fun onMessage(channel: String, message: String) { trySend(message) }
            }
            val subJob = ioScope.launch {
                runCatching { jedis.subscribe(sub, channelKey(userId)) }
                    .onFailure { log.warn("Redis subscribe error userId={}: {}", userId, it.message) }
            }
            awaitClose {
                runCatching { sub.unsubscribe() }
                runCatching { jedis.close() }
                subJob.cancel()
            }
        }
    }

    suspend fun recentRuns(userId: String): List<PipelineRunState> {
        if (!inRedis) return memRuns[userId]?.toList()?.reversed() ?: emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                pool!!.resource.use { jedis ->
                    jedis.lrange(runsListKey(userId), 0, MAX_RUNS - 1).mapNotNull { id ->
                        jedis.get(runStateKey(id))
                            ?.let { runCatching { json.decodeFromString<PipelineRunState>(it) }.getOrNull() }
                    }
                }
            }.getOrElse { log.warn("Redis recentRuns failed: {}", it.message); emptyList() }
        }
    }

    fun startRun(
        userId: String,
        repo: String,
        branch: String,
        commitShort: String,
        projectId: String? = null,
        retriedFromRunId: String? = null
    ): String {
        val runId = UUID.randomUUID().toString()
        val steps = initialSteps().toMutableList()
        activeSteps[runId] = steps
        val snapshot = PipelineRunState(
            runId = runId, repo = repo, branch = branch,
            commitShort = commitShort, startedAt = System.currentTimeMillis(),
            steps = steps.toList(), projectId = projectId, retriedFromRunId = retriedFromRunId
        )
        storeRun(userId, snapshot)
        emit(userId, PipelineEvent(type = "start", runId = runId, snapshot = snapshot))
        return runId
    }

    fun stepRunning(userId: String, runId: String, stepId: String, detail: String? = null) =
        updateStep(userId, runId, stepId, "running", detail)

    fun stepDone(userId: String, runId: String, stepId: String, detail: String? = null) =
        updateStep(userId, runId, stepId, "done", detail)

    fun stepSkipped(userId: String, runId: String, stepId: String, detail: String? = null) =
        updateStep(userId, runId, stepId, "skipped", detail)

    fun stepError(userId: String, runId: String, stepId: String, detail: String? = null) =
        updateStep(userId, runId, stepId, "error", detail)

    fun finishRun(
        userId: String,
        runId: String,
        prUrl: String? = null,
        error: String? = null,
        surfaceSkipped: Int = 0
    ) {
        val finishedAt = System.currentTimeMillis()
        mutateRun(userId, runId) {
            it.copy(finishedAt = finishedAt, prUrl = prUrl, error = error, surfaceSkipped = surfaceSkipped)
        }
        activeSteps.remove(runId)
        emit(userId, PipelineEvent(
            type = "finish", runId = runId, prUrl = prUrl, error = error,
            surfaceSkipped = surfaceSkipped.takeIf { it > 0 }
        ))
    }

    fun close() {
        runCatching { pool?.close() }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun updateStep(userId: String, runId: String, stepId: String, status: String, detail: String?) {
        val steps = activeSteps[runId] ?: return
        val idx = steps.indexOfFirst { it.id == stepId }
        if (idx >= 0) steps[idx] = steps[idx].copy(status = status, detail = detail)
        mutateRun(userId, runId) { it.copy(steps = steps.toList()) }
        emit(userId, PipelineEvent(type = "step", runId = runId, stepId = stepId, status = status, detail = detail))
    }

    private fun storeRun(userId: String, snapshot: PipelineRunState) {
        if (inRedis) {
            val encoded = json.encodeToString(snapshot)
            ioScope.launch {
                runCatching {
                    pool!!.resource.use { jedis ->
                        jedis.setex(runStateKey(snapshot.runId), RUN_TTL, encoded)
                        jedis.lpush(runsListKey(userId), snapshot.runId)
                        jedis.ltrim(runsListKey(userId), 0, MAX_RUNS - 1)
                        jedis.expire(runsListKey(userId), RUN_TTL)
                    }
                }.onFailure { log.warn("Redis storeRun failed runId={}: {}", snapshot.runId, it.message) }
            }
        } else {
            val deque = memRuns.getOrPut(userId) { ArrayDeque(20) }
            synchronized(deque) { deque.addFirst(snapshot); while (deque.size > 20) deque.removeLast() }
        }
    }

    private fun mutateRun(userId: String, runId: String, transform: (PipelineRunState) -> PipelineRunState) {
        if (inRedis) {
            ioScope.launch {
                runCatching {
                    pool!!.resource.use { jedis ->
                        val current = jedis.get(runStateKey(runId))
                            ?.let { runCatching { json.decodeFromString<PipelineRunState>(it) }.getOrNull() }
                            ?: return@launch
                        jedis.setex(runStateKey(runId), RUN_TTL, json.encodeToString(transform(current)))
                    }
                }.onFailure { log.warn("Redis mutateRun failed runId={}: {}", runId, it.message) }
            }
        } else {
            val deque = memRuns[userId] ?: return
            synchronized(deque) {
                val idx = deque.indexOfFirst { it.runId == runId }
                if (idx >= 0) deque[idx] = transform(deque[idx])
            }
        }
    }

    private fun emit(userId: String, event: PipelineEvent) {
        val encoded = json.encodeToString(event)
        if (inRedis) {
            ioScope.launch {
                runCatching {
                    pool!!.resource.use { jedis -> jedis.publish(channelKey(userId), encoded) }
                }.onFailure { log.warn("Redis publish failed userId={}: {}", userId, it.message) }
            }
        } else {
            memFlowFor(userId).tryEmit(encoded)
        }
    }

    private fun memFlowFor(userId: String) =
        memFlows.getOrPut(userId) { MutableSharedFlow(replay = 40, extraBufferCapacity = 64) }
}
