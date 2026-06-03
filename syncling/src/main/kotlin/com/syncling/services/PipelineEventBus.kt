package com.syncling.services

import com.syncling.domain.LocaleProgressState
import com.syncling.domain.PipelineRunState
import com.syncling.domain.PipelineRunSummary
import com.syncling.domain.PipelineStepState
import com.syncling.domain.initialSteps
import com.syncling.repository.PipelineRunRepository
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.params.ScanParams
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class PipelineEvent(
    val type: String,            // start | step | finish | onboarding | cdn_ready | notification
    val runId: String = "",
    val stepId: String? = null,
    val status: String? = null,
    val detail: String? = null,
    val prUrl: String? = null,
    val error: String? = null,
    val snapshot: PipelineRunState? = null,
    val surfaceSkipped: Int? = null,
    val onboardingStep: String? = null,
    val cdnBundleVersion: String? = null,
    val cdnLocales: List<String>? = null,
    // Notification fields — populated when type = "notification"
    val notificationId: String? = null,
    val notificationTitle: String? = null,
    val notificationMessage: String? = null,
    val notificationLevel: String? = null,
    val notificationActionUrl: String? = null,
    val notificationActionLabel: String? = null,
    // Per-locale lane updates emitted during the TRANSLATING phase.
    // type = "locale" carries one entry; the snapshot caches the full set.
    val locale: LocaleProgressState? = null,
    val locales: List<LocaleProgressState>? = null,
    val progressDone: Int? = null,
    val progressTotal: Int? = null,
    // Webhook rejected — type = "webhook_rejected". Fired when a push arrives but the
    // pipeline is NOT started, so the user knows why nothing appeared in their activity feed.
    val rejectedReason: String? = null,  // branch_mismatch | source_not_modified | usage_limit | rate_limited
    val rejectedDetail: String? = null,  // human-readable explanation
    val rejectedRepo: String? = null,
    val rejectedBranch: String? = null,
    val rejectedProjectId: String? = null,
    // Support chat — type = "support_message". Delivered to user's SSE channel when admin replies.
    val supportTicketId: String? = null,
    val supportSenderType: String? = null,   // "user" | "admin"
    val supportTicketStatus: String? = null, // ticket status after the event
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
class PipelineEventBus(
    private val redisUrl: String? = null,
    /**
     * Optional long-term run persistence. When supplied, every [finishRun] call also
     * writes a [PipelineRunSummary] to Mongo for analytics queries that span beyond
     * the 24h Redis TTL. Null in tests / no-Mongo envs.
     */
    private val runRepository: PipelineRunRepository? = null
) {

    private val log = LoggerFactory.getLogger(PipelineEventBus::class.java)
    private val json = Json { encodeDefaults = false }

    private val pool: JedisPool? = redisUrl?.takeIf { it.isNotBlank() }?.let { url ->
        runCatching {
            JedisPool(JedisPoolConfig().apply { maxTotal = 16; maxIdle = 6 }, URI(url))
                .also { p -> p.resource.use { j -> j.ping() } }
        }.onSuccess { log.info("PipelineEventBus: Redis pool enabled") }
         .onFailure { log.warn("PipelineEventBus: Redis unavailable ({}), using in-memory fallback", it.message) }
         .getOrNull()
    }

    // Lettuce reactive pub/sub — one connection handles all SSE subscribers, zero blocking threads.
    private val lettuceClient: RedisClient? = redisUrl?.takeIf { it.isNotBlank() }?.let { url ->
        runCatching { RedisClient.create(url) }
            .onFailure { log.warn("PipelineEventBus: Lettuce client init failed: {}", it.message) }
            .getOrNull()
    }

    // Per-user SharedFlows that the single Lettuce listener dispatches into.
    private val subFlows = ConcurrentHashMap<String, MutableSharedFlow<String>>()
    private val subCounts = ConcurrentHashMap<String, AtomicInteger>()

    private val pubSubConn: StatefulRedisPubSubConnection<String, String>? = lettuceClient?.let { c ->
        runCatching {
            c.connectPubSub().also { conn ->
                conn.addListener(object : RedisPubSubAdapter<String, String>() {
                    override fun message(channel: String, message: String) {
                        val userId = channel.removePrefix("tl:events:")
                        subFlows[userId]?.tryEmit(message)
                    }
                })
            }
        }.onSuccess { log.info("PipelineEventBus: Lettuce reactive pub/sub connected") }
         .onFailure { log.warn("PipelineEventBus: Lettuce pub/sub connect failed: {}", it.message) }
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
        // Fall back to in-memory if Redis is unavailable or Lettuce failed to connect.
        if (!inRedis || pubSubConn == null) return memFlowFor(userId).asSharedFlow()

        // Get-or-create a shared flow for this user. All concurrent SSE connections for
        // the same user share one flow and one Redis channel subscription (ref-counted).
        val flow = subFlows.getOrPut(userId) { MutableSharedFlow(replay = 0, extraBufferCapacity = 64) }
        val count = subCounts.getOrPut(userId) { AtomicInteger(0) }
        if (count.getAndIncrement() == 0) {
            // First subscriber — open the Redis channel subscription. Non-blocking.
            // Guard against a degraded Lettuce connection; fall back to in-memory rather than
            // letting the exception escape into the SSE respondBytesWriter and crashing the stream.
            runCatching { pubSubConn?.async()?.subscribe(channelKey(userId)) }
                .onFailure {
                    log.warn("Lettuce subscribe failed for userId={} — falling back to in-memory flow: {}", userId, it.message)
                    count.decrementAndGet()
                    return memFlowFor(userId).asSharedFlow()
                }
        }

        return callbackFlow {
            val job = ioScope.launch { flow.collect { trySend(it) } }
            awaitClose {
                job.cancel()
                if (count.decrementAndGet() == 0) {
                    // Last subscriber disconnected — release the Redis channel subscription.
                    subFlows.remove(userId)
                    subCounts.remove(userId)
                    runCatching { pubSubConn.async().unsubscribe(channelKey(userId)) }
                }
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
        retriedFromRunId: String? = null,
        retryCount: Int = 0,
        triggeredByUserId: String? = null
    ): String {
        val runId = UUID.randomUUID().toString()
        val steps = initialSteps().toMutableList()
        activeSteps[runId] = steps
        // Owner == the userId passed in (the run's billing target). The label distinguishes
        // owner-triggered, member-triggered, and external (webhook author not matched).
        val label = when {
            triggeredByUserId == null -> "external"
            triggeredByUserId == userId -> "owner"
            else -> "member"
        }
        val snapshot = PipelineRunState(
            runId = runId, repo = repo, branch = branch,
            commitShort = commitShort, startedAt = System.currentTimeMillis(),
            steps = steps.toList(), projectId = projectId, ownerId = userId,
            retriedFromRunId = retriedFromRunId, retryCount = retryCount,
            triggeredByUserId = triggeredByUserId, triggeredByLabel = label
        )
        storeRun(userId, snapshot)
        emit(userId, PipelineEvent(type = "start", runId = runId, snapshot = snapshot))
        return runId
    }

    /** Pushes an onboarding step change to any open dashboard SSE connection for this user. */
    fun emitOnboardingStep(userId: String, step: String) {
        emit(userId, PipelineEvent(type = "onboarding", onboardingStep = step))
    }

    fun emitNotification(
        userId: String,
        notificationId: String,
        title: String,
        message: String,
        level: String,
        actionUrl: String?,
        actionLabel: String?
    ) {
        emit(userId, PipelineEvent(
            type = "notification",
            notificationId = notificationId,
            notificationTitle = title,
            notificationMessage = message,
            notificationLevel = level,
            notificationActionUrl = actionUrl,
            notificationActionLabel = actionLabel
        ))
    }

    fun emitCdnReady(userId: String, runId: String, bundleVersion: String, locales: List<String>) {
        emit(userId, PipelineEvent(
            type = "cdn_ready",
            runId = runId,
            cdnBundleVersion = bundleVersion,
            cdnLocales = locales
        ))
    }

    fun emitSupportMessage(userId: String, ticketId: String, senderType: String, ticketStatus: String) {
        emit(userId, PipelineEvent(
            type = "support_message",
            supportTicketId = ticketId,
            supportSenderType = senderType,
            supportTicketStatus = ticketStatus,
        ))
    }

    fun emitWebhookRejected(
        ownerId: String,
        repo: String,
        branch: String,
        projectId: String,
        reason: String,
        detail: String
    ) {
        emit(ownerId, PipelineEvent(
            type = "webhook_rejected",
            rejectedReason = reason,
            rejectedDetail = detail,
            rejectedRepo = repo,
            rejectedBranch = branch,
            rejectedProjectId = projectId
        ))
    }

    /**
     * Patches an already-finished run with the outcome of an out-of-band
     * post-approval follow-up: the PR that was created (or appended to) and any
     * CDN publish that ran afterwards. The original run card on every open
     * dashboard is updated in place — CREATING_PR / CDN_PUBLISH steps flip to
     * "done", the PR link appears, and the CDN widget refreshes — instead of
     * being stuck on "No translatable strings approved".
     *
     * Bypasses [updateStep] / [activeSteps] because those are scoped to in-flight
     * runs; this writes directly to the stored snapshot and re-emits the same
     * SSE event shapes the client already handles. Safe to call when no recent
     * run exists for the project (it's a no-op).
     */
    suspend fun recordPostApprovalUpdate(
        projectId: String,
        ownerId: String,
        prUrl: String?,
        cdnDetail: String? = null,
        cdnBundleVersion: String? = null,
        cdnLocales: List<String> = emptyList()
    ) {
        val target = runCatching { recentRuns(ownerId) }.getOrElse { emptyList() }
            .filter { it.projectId == projectId }
            .maxByOrNull { it.startedAt } ?: return
        val runId = target.runId

        mutateRun(ownerId, runId) { current ->
            val newSteps = current.steps.map { step ->
                when {
                    step.id == "CREATING_PR" && prUrl != null ->
                        step.copy(status = "done", detail = prUrl)
                    step.id == "CDN_PUBLISH" && cdnDetail != null ->
                        step.copy(status = "done", detail = cdnDetail)
                    else -> step
                }
            }
            current.copy(steps = newSteps, prUrl = prUrl ?: current.prUrl)
        }

        if (prUrl != null) {
            emit(ownerId, PipelineEvent(
                type = "step", runId = runId, stepId = "CREATING_PR", status = "done", detail = prUrl
            ))
            // Re-emit finish so the client populates run.prUrl and the footer
            // shows the "View pull request" link instead of "No changes found".
            emit(ownerId, PipelineEvent(type = "finish", runId = runId, prUrl = prUrl))
        }
        if (cdnDetail != null) {
            emit(ownerId, PipelineEvent(
                type = "step", runId = runId, stepId = "CDN_PUBLISH", status = "done", detail = cdnDetail
            ))
        }
        if (cdnBundleVersion != null) {
            emit(ownerId, PipelineEvent(
                type = "cdn_ready", runId = runId,
                cdnBundleVersion = cdnBundleVersion, cdnLocales = cdnLocales
            ))
        }
    }

    /**
     * Seeds the per-locale lanes for this run at the start of the TRANSLATING phase.
     * Each lane is initialised with total=0 (filled in once we know batch counts) and
     * status="queued". Subsequent [emitLocaleProgress] calls advance individual lanes.
     */
    fun seedLocales(userId: String, runId: String, locales: List<LocaleProgressState>) {
        mutateRun(userId, runId) { it.copy(locales = locales, progressDone = 0, progressTotal = locales.sumOf { l -> l.total }) }
        emit(userId, PipelineEvent(type = "locales", runId = runId, locales = locales,
            progressDone = 0, progressTotal = locales.sumOf { it.total }))
    }

    /**
     * Advances one locale lane and refreshes the aggregate progress counters.
     * Safe to call from concurrent locale coroutines — the snapshot mutation
     * runs serially on [ioScope] and the wire event is fire-and-forget.
     */
    fun emitLocaleProgress(userId: String, runId: String, locale: LocaleProgressState) {
        mutateRun(userId, runId) { current ->
            val updated = current.locales.map { if (it.code == locale.code) locale else it }
                .let { if (it.none { l -> l.code == locale.code }) it + locale else it }
            val done = updated.sumOf { it.done }
            val total = updated.sumOf { it.total }.coerceAtLeast(current.progressTotal)
            current.copy(locales = updated, progressDone = done, progressTotal = total)
        }
        emit(userId, PipelineEvent(type = "locale", runId = runId, locale = locale))
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
        prBranch: String? = null,
        error: String? = null,
        surfaceSkipped: Int = 0,
        stringsTranslated: Int = 0,
        stringsPerLocale: Map<String, Int> = emptyMap(),
        cacheHits: Int = 0
    ) {
        val finishedAt = System.currentTimeMillis()
        mutateRun(userId, runId) {
            it.copy(finishedAt = finishedAt, prUrl = prUrl, prBranch = prBranch, error = error, surfaceSkipped = surfaceSkipped)
        }
        activeSteps.remove(runId)
        emit(userId, PipelineEvent(
            type = "finish", runId = runId, prUrl = prUrl, error = error,
            surfaceSkipped = surfaceSkipped.takeIf { it > 0 }
        ))
        persistRunSummary(userId, runId, finishedAt, error, stringsTranslated, stringsPerLocale, cacheHits)
    }

    private fun persistRunSummary(
        userId: String, runId: String, finishedAt: Long,
        error: String?, stringsTranslated: Int, stringsPerLocale: Map<String, Int>,
        cacheHits: Int = 0
    ) {
        val repo = runRepository ?: return
        // Read back the snapshot we just mutated so the summary includes the
        // trigger metadata that startRun captured. Done off the request thread.
        ioScope.launch {
            runCatching {
                val snap = loadRun(userId, runId) ?: return@launch
                val status = when {
                    error != null -> "failed"
                    snap.steps.any { it.status == "error" } -> "failed"
                    snap.steps.any { it.status == "skipped" } && stringsTranslated == 0 -> "succeeded"
                    else -> "succeeded"
                }
                val summary = PipelineRunSummary(
                    runId = runId,
                    projectId = snap.projectId ?: "",
                    ownerId = snap.ownerId ?: userId,
                    triggeredByUserId = snap.triggeredByUserId,
                    triggeredByLabel = snap.triggeredByLabel,
                    repo = snap.repo,
                    branch = snap.branch,
                    commitShort = snap.commitShort,
                    startedAt = snap.startedAt,
                    finishedAt = finishedAt,
                    durationMs = finishedAt - snap.startedAt,
                    status = status,
                    stringsTranslated = stringsTranslated,
                    stringsPerLocale = stringsPerLocale,
                    error = error,
                    cacheHits = cacheHits
                )
                repo.persist(summary)
            }.onFailure { log.warn("persistRunSummary failed runId={}: {}", runId, it.message) }
        }
    }

    private suspend fun loadRun(userId: String, runId: String): PipelineRunState? {
        if (inRedis) {
            return runCatching {
                pool!!.resource.use { jedis ->
                    jedis.get(runStateKey(runId))
                        ?.let { runCatching { json.decodeFromString<PipelineRunState>(it) }.getOrNull() }
                }
            }.getOrNull()
        }
        val deque = memRuns[userId] ?: return null
        return synchronized(deque) { deque.firstOrNull { it.runId == runId } }
    }

    /**
     * Scans all known run lists for runs that are still "in-progress" (no finishedAt)
     * but started more than [stuckAfterMs] milliseconds ago. These are pipelines that
     * died mid-run without ever calling finishRun — e.g. the server crashed, a timeout
     * wasn't caught, or a coroutine leaked. Called once every 24 hours from Application.kt.
     *
     * Returns the number of runs that were force-finished.
     */
    suspend fun cleanupStuckRuns(stuckAfterMs: Long = 2L * 3600 * 1000): Int {
        val cutoff = System.currentTimeMillis() - stuckAfterMs
        val stuckError = "Pipeline timed out — this run was stuck and has been automatically cancelled."

        if (!inRedis) {
            var count = 0
            memRuns.forEach { (userId, deque) ->
                synchronized(deque) {
                    deque.indices.forEach { idx ->
                        val run = deque[idx]
                        if (run.finishedAt == null && run.startedAt < cutoff) {
                            deque[idx] = run.copy(finishedAt = System.currentTimeMillis(), error = stuckError)
                            activeSteps.remove(run.runId)
                            emit(userId, PipelineEvent(type = "finish", runId = run.runId, error = stuckError))
                            count++
                        }
                    }
                }
            }
            return count
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                // Phase 1: collect all stuck runs by scanning tl:runs:* keys.
                data class StuckRun(val userId: String, val runId: String)
                val stuck = mutableListOf<StuckRun>()
                pool!!.resource.use { jedis ->
                    var cursor = "0"
                    val scanParams = ScanParams().match("tl:runs:*").count(200)
                    do {
                        val result = jedis.scan(cursor, scanParams)
                        cursor = result.cursor
                        for (listKey in result.result) {
                            val userId = listKey.removePrefix("tl:runs:")
                            val runIds = jedis.lrange(listKey, 0, MAX_RUNS - 1)
                            for (runId in runIds) {
                                val raw = jedis.get(runStateKey(runId)) ?: continue
                                val run = runCatching { json.decodeFromString<PipelineRunState>(raw) }.getOrNull()
                                    ?: continue
                                if (run.finishedAt == null && run.startedAt < cutoff) {
                                    stuck.add(StuckRun(userId, runId))
                                }
                            }
                        }
                    } while (cursor != "0")
                }
                // Phase 2: force-finish each stuck run. Uses finishRun so the snapshot is
                // updated, the SSE event is broadcast, and the Mongo summary is persisted.
                for (sr in stuck) {
                    runCatching {
                        log.warn("stuckRunCleanup: force-finishing runId={} userId={}", sr.runId, sr.userId)
                        finishRun(sr.userId, sr.runId, error = stuckError)
                    }.onFailure { log.warn("stuckRunCleanup: failed to finish runId={}: {}", sr.runId, it.message) }
                }
                stuck.size
            }.getOrElse { log.warn("stuckRunCleanup scan failed: {}", it.message); 0 }
        }
    }

    fun close() {
        // Cancel the ioScope first so in-flight launch coroutines don't try to use
        // the Jedis pool after it's closed, which would log spurious errors.
        ioScope.cancel()
        runCatching { pubSubConn?.close() }
        runCatching { lettuceClient?.shutdown() }
        runCatching { pool?.close() }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun updateStep(userId: String, runId: String, stepId: String, status: String, detail: String?) {
        val steps = activeSteps[runId] ?: return
        // Locale coroutines call stepRunning/stepDone concurrently — synchronize on the
        // list itself so concurrent indexed writes don't corrupt the step array.
        val snapshot = synchronized(steps) {
            val idx = steps.indexOfFirst { it.id == stepId }
            if (idx >= 0) steps[idx] = steps[idx].copy(status = status, detail = detail)
            steps.toList()
        }
        mutateRun(userId, runId) { it.copy(steps = snapshot) }
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
        memFlows.getOrPut(userId) { MutableSharedFlow(replay = 0, extraBufferCapacity = 64) }
}
