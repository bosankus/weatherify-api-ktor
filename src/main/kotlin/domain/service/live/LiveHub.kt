package domain.service.live

import domain.model.live.LiveChannel
import domain.model.live.LiveEvent
import domain.model.live.LocationRef
import domain.model.live.ServerMessage
import domain.model.live.TopicAck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import util.LiveTopic
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Central registry: which connection is subscribed to which topic, and one upstream poller per
 * *topic* rather than per connection.
 *
 * The whole economic argument for this feature lives in [subscribe] and [pollerLoop]:
 *
 *   naive   — every connected client polls upstream:   calls/day = users x (86400 / cadence)
 *   this    — every distinct cell polls upstream once: calls/day = cells x (86400 / cadence)
 *
 * With 500 users watching 40 cells that is 72,000/day versus 5,760/day, and the second number
 * stays flat as users grow. Users scale with your success; cells scale with geography.
 *
 * Scope note: this implementation fans out *within one JVM*. Running more than one instance
 * means a client connected to pod A never sees events produced by pod B's poller. The seam for
 * fixing that is deliberately in one place — [publish] — which is where a Redis pub/sub bridge
 * gets inserted. See docs/live-weather-websocket.md, "Scaling past one instance".
 */
class LiveHub(
    private val scope: CoroutineScope,
    private val source: ConditionsSource,
    /**
     * Floor on how often a topic may hit the data source, regardless of what subscribers want.
     * For the real vendor this must stay at or above ~300s: One Call refreshes roughly every
     * 10 minutes, so polling faster burns quota to receive identical bytes.
     */
    private val minPollSeconds: Int,
    /** How long a topic's poller lingers after its last subscriber leaves. */
    private val idleGraceSeconds: Int = 60
) {
    private val log = LoggerFactory.getLogger(LiveHub::class.java)

    /** topic -> subscribers. Set semantics: a connection subscribing twice is idempotent. */
    private val subscribers = ConcurrentHashMap<String, MutableSet<LiveConnection>>()

    /** topic -> poller state. Created on first subscriber, torn down after the last one leaves. */
    private val topics = ConcurrentHashMap<String, TopicRuntime>()

    private class TopicRuntime(val topic: String) {
        @Volatile var job: Job? = null
        @Volatile var lastSnapshot: LocationSnapshot? = null
        @Volatile var teardownJob: Job? = null
        val seq = AtomicLong(0)

        /**
         * Serializes "assign the next seq number" with "enqueue it to subscribers" across the
         * two independent producers that touch this topic's sequence: a newly subscribing
         * connection's immediate welcome snapshot, and the poller's fan-out on each tick.
         *
         * Without this, a subscribe() and a concurrent publish() on the same topic could each
         * grab a seq number (atomically, so the numbers themselves are never duplicated) but then
         * land in a connection's queue in the *other* order — e.g. seq 44 enqueued before seq 43.
         * AtomicLong only orders the counter; it says nothing about the delivery that follows it.
         * The critical section here is pure in-memory work with no suspension, so a plain
         * monitor is correct and cheap — no need for a coroutine Mutex.
         */
        val deliveryLock = Any()
    }

    // -----------------------------------------------------------------------
    // Subscription management
    // -----------------------------------------------------------------------

    /**
     * Subscribe [connection] to the cell containing [ref].
     *
     * Returns the ack the client needs: the canonical topic key, the current sequence number,
     * and whether the client's cursor is too stale to reconcile.
     */
    fun subscribe(connection: LiveConnection, ref: LocationRef, clientCursor: Long?): TopicAck {
        val topic = LiveTopic.forCoordinates(ref.lat, ref.lon)

        val set = subscribers.computeIfAbsent(topic) { ConcurrentHashMap.newKeySet() }
        val isFirstSubscriber = set.isEmpty()
        set.add(connection)
        connection.topics.add(topic)

        val runtime = topics.computeIfAbsent(topic) { TopicRuntime(topic) }

        // A reconnect inside the grace window reuses the warm poller and its retained snapshot,
        // so a flapping mobile connection never causes repeated upstream fetches.
        runtime.teardownJob?.cancel()
        runtime.teardownJob = null

        // The poller loop re-reads its interval on every iteration, so a new premium subscriber
        // speeds up an already-running topic on its next tick without needing a restart.
        if (isFirstSubscriber || runtime.job?.isActive != true) {
            startPoller(runtime)
        }

        val currentSeq = runtime.seq.get()
        val (lat, lon) = LiveTopic.centerOf(topic)

        // If the client is behind by more than what we can replay (we retain no event history,
        // only the latest snapshot), tell it so. It must then treat the snapshot we are about to
        // send as a full reset rather than applying it as a delta.
        val gap = clientCursor != null && clientCursor < currentSeq

        // Every subscribe gets an immediate snapshot so the UI has something to paint at once,
        // rather than waiting up to a full poll interval for the first frame.
        synchronized(runtime.deliveryLock) {
            runtime.lastSnapshot?.let { snapshot ->
                val seq = runtime.seq.incrementAndGet()
                deliver(
                    connection,
                    topic,
                    seq,
                    LiveEvent.Snapshot(snapshot.conditions, snapshot.aqi, snapshot.alerts)
                )
            }
        }

        return TopicAck(
            topic = topic,
            label = ref.label,
            lat = lat,
            lon = lon,
            seq = currentSeq,
            gap = gap
        )
    }

    fun unsubscribe(connection: LiveConnection, topic: String) {
        val set = subscribers[topic] ?: return
        set.remove(connection)
        connection.forgetTopic(topic)
        if (set.isEmpty()) scheduleTeardown(topic)
    }

    /** Called when a socket closes, for any reason. Must never throw. */
    fun removeConnection(connection: LiveConnection) {
        connection.topics.toList().forEach { topic ->
            subscribers[topic]?.let { set ->
                set.remove(connection)
                if (set.isEmpty()) scheduleTeardown(topic)
            }
        }
        connection.topics.clear()
        log.debug(
            "Connection {} removed (user={}, droppedFrames={})",
            connection.id, connection.email, connection.droppedFrames.get()
        )
    }

    // -----------------------------------------------------------------------
    // Polling
    // -----------------------------------------------------------------------

    /**
     * Interval this topic should poll at: the fastest cadence any current subscriber is entitled
     * to, floored by [minPollSeconds]. One premium subscriber therefore speeds the topic up for
     * everyone watching it — but the per-connection throttle in
     * [LiveConnection.shouldDeliver] still holds free-tier clients to their own cadence, so the
     * entitlement is preserved at delivery time rather than at fetch time.
     */
    private fun pollIntervalSecondsFor(topic: String): Int {
        val fastest = subscribers[topic]
            ?.minOfOrNull { it.entitlements.cadenceSeconds }
            ?: minPollSeconds
        return maxOf(minPollSeconds, fastest)
    }

    private fun startPoller(runtime: TopicRuntime) {
        runtime.job?.cancel()
        runtime.job = scope.launch { pollerLoop(runtime) }
        log.info(
            "Started poller for topic={} interval={}s (topics active={})",
            runtime.topic, pollIntervalSecondsFor(runtime.topic), topics.size
        )
    }

    private suspend fun pollerLoop(runtime: TopicRuntime) {
        val topic = runtime.topic
        val (lat, lon) = LiveTopic.centerOf(topic)

        while (scope.isActive) {
            val hasAqiSubscriber = subscribers[topic]
                ?.any { LiveChannel.AQI in it.channels } == true

            try {
                val snapshot = source.fetch(lat, lon, hasAqiSubscriber)
                if (snapshot != null) {
                    val events = LiveEventDeriver.derive(runtime.lastSnapshot, snapshot)
                    runtime.lastSnapshot = snapshot
                    events.forEach { event -> publish(topic, runtime, event) }
                }
            } catch (e: Exception) {
                // A poller must never die on a transient upstream failure: if it did, every
                // subscriber would silently stop receiving updates with the socket still open,
                // which looks like a frozen app rather than an error.
                log.warn("Poll failed for topic={}: {}", topic, e.message)
            }

            delay(pollIntervalSecondsFor(topic) * 1000L)
        }
    }

    // -----------------------------------------------------------------------
    // Fan-out
    // -----------------------------------------------------------------------

    /**
     * Single fan-out point. Everything a client receives passes through here, which is why this
     * is the correct and only place to bridge to Redis pub/sub when scaling past one instance.
     */
    private fun publish(topic: String, runtime: TopicRuntime, event: LiveEvent) {
        val set = subscribers[topic] ?: return
        if (set.isEmpty()) return

        // Sequence is assigned once per event per topic, not per recipient, so every subscriber
        // observes the same ordering and the same numbers. That is what makes the client's
        // gap detection meaningful.
        //
        // Guarded by the same lock subscribe() uses for its welcome snapshot: assigning the
        // number and enqueueing it are not atomic with each other by default, so without a shared
        // lock a subscribe() racing this tick could hand a *later* connection a *lower* seq than
        // one already in flight here, or vice versa — a client-visible seq inversion rather than
        // just a gap. See TopicRuntime.deliveryLock.
        synchronized(runtime.deliveryLock) {
            val seq = runtime.seq.incrementAndGet()
            val now = System.currentTimeMillis()

            set.forEach { connection ->
                if (connection.shouldDeliver(topic, event, now)) {
                    deliver(connection, topic, seq, event)
                }
            }
        }
    }

    private fun deliver(connection: LiveConnection, topic: String, seq: Long, event: LiveEvent) {
        val alive = connection.offer(
            ServerMessage.Event(
                topic = topic,
                seq = seq,
                ts = System.currentTimeMillis(),
                event = event
            )
        )
        if (!alive) removeConnection(connection)
    }

    // -----------------------------------------------------------------------
    // Teardown
    // -----------------------------------------------------------------------

    /**
     * Stop polling a topic nobody is watching — but not immediately.
     *
     * Mobile clients disconnect constantly: screen lock, tunnel, cell handover. Tearing the
     * poller down the instant the last subscriber vanishes would mean a five-second walk through
     * a lift costs a cold start and a fresh upstream call on the way back. The grace window makes
     * reconnects free.
     */
    private fun scheduleTeardown(topic: String) {
        val runtime = topics[topic] ?: return
        if (runtime.teardownJob?.isActive == true) return

        runtime.teardownJob = scope.launch {
            delay(idleGraceSeconds * 1000L)
            if (subscribers[topic]?.isEmpty() != false) {
                runtime.job?.cancel()
                topics.remove(topic)
                subscribers.remove(topic)
                log.info("Tore down idle poller for topic={} (topics active={})", topic, topics.size)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Observability
    // -----------------------------------------------------------------------

    data class Stats(
        val activeTopics: Int,
        val activeConnections: Int,
        val subscriptions: Int,
        val droppedFrames: Long
    )

    fun stats(): Stats {
        val connections = subscribers.values.flatten().toSet()
        return Stats(
            activeTopics = topics.size,
            activeConnections = connections.size,
            subscriptions = subscribers.values.sumOf { it.size },
            droppedFrames = connections.sumOf { it.droppedFrames.get() }
        )
    }
}
