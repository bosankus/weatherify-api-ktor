package domain.service.live

import domain.model.live.LiveChannel
import domain.model.live.LiveEvent
import domain.model.live.ServerMessage
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * One connected client.
 *
 * The outbound [queue] is the backpressure boundary, and it is the single most important design
 * decision in the whole feature.
 *
 * A WebSocket write can block. A phone on a bad 2G link, or one that got suspended by the OS
 * mid-frame, will stop draining its TCP window. If the fan-out loop wrote directly to sockets,
 * that one phone would stall the coroutine pushing to *every other* subscriber of that topic —
 * one slow client degrading everybody. Classic head-of-line blocking.
 *
 * So fan-out never writes to a socket. It offers to a bounded per-connection queue and returns
 * immediately. A dedicated writer coroutine per connection drains that queue into the socket at
 * whatever pace the client can actually sustain. If the queue fills, that client is too slow and
 * we degrade *only that client*.
 */
class LiveConnection(
    val id: String,
    val email: String,
    val entitlements: LiveEntitlements,
    /** Channels this connection asked for, already intersected with its entitlements. */
    @Volatile var channels: Set<String> = entitlements.allowedChannels.toSet(),
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
) {
    private val log = LoggerFactory.getLogger(LiveConnection::class.java)

    /**
     * Bounded, never-suspending from the producer side. We use trySend and handle rejection
     * explicitly rather than SUSPEND, because suspending here would reintroduce the exact
     * head-of-line blocking the queue exists to prevent.
     */
    val queue: Channel<ServerMessage> = Channel(capacity = queueCapacity)

    /** Topics this connection currently receives. */
    val topics: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Per-topic timestamp of the last CONDITIONS frame, used for tier-based throttling. */
    private val lastConditionsSentAt = ConcurrentHashMap<String, Long>()

    /** Observability counters — these are what you page on, not the happy path. */
    val droppedFrames = AtomicLong(0)
    private val criticalBacklogStrikes = AtomicInteger(0)

    @Volatile
    var closeRequested: Boolean = false
        private set

    @Volatile
    var closeCode: Short = 1000

    /**
     * Offer a frame to this connection.
     *
     * @return false when the connection should be torn down (too far behind on critical traffic)
     */
    fun offer(message: ServerMessage): Boolean {
        val result = queue.trySend(message)
        if (result.isSuccess) {
            criticalBacklogStrikes.set(0)
            return true
        }

        // Queue is full. What we do next depends on whether the frame mattered.
        val critical = message is ServerMessage.Event && isCritical(message.event)
        if (!critical) {
            // Lossy by design: a stale temperature reading has no value once a newer one exists.
            droppedFrames.incrementAndGet()
            return true
        }

        // An alert could not be delivered. Dropping it silently would be the worst possible
        // outcome for this feature, so we give the client a few intervals to catch up and then
        // disconnect it. A disconnect is recoverable — the client reconnects and resubscribes,
        // and the fresh snapshot carries the active alerts. A silently swallowed alert is not.
        val strikes = criticalBacklogStrikes.incrementAndGet()
        droppedFrames.incrementAndGet()
        if (strikes >= MAX_CRITICAL_STRIKES) {
            log.warn(
                "Connection {} (user={}) dropped {} critical frames; closing as slow consumer",
                id, email, strikes
            )
            requestClose(LiveCloseCodes.SLOW_CONSUMER)
            return false
        }
        return true
    }

    /**
     * Tier-aware filter applied before [offer].
     *
     * Free and premium subscribers of the same topic share one poller, so they see the same
     * event stream. Rather than running two pollers at two cadences, we run one at the fastest
     * cadence any subscriber needs and throttle per connection here. Alerts bypass the throttle
     * entirely — cadence is a product lever on convenience data, never on safety data.
     */
    fun shouldDeliver(topic: String, event: LiveEvent, now: Long): Boolean {
        val channel = LiveChannel.of(event)
        if (channel !in channels) return false

        if (channel != LiveChannel.CONDITIONS) return true
        if (event is LiveEvent.Snapshot) return true

        val last = lastConditionsSentAt[topic] ?: 0L
        if (now - last < entitlements.cadenceSeconds * 1000L) return false
        lastConditionsSentAt[topic] = now
        return true
    }

    fun requestClose(code: Short) {
        closeRequested = true
        closeCode = code
        queue.close()
    }

    fun forgetTopic(topic: String) {
        topics.remove(topic)
        lastConditionsSentAt.remove(topic)
    }

    private fun isCritical(event: LiveEvent): Boolean =
        event is LiveEvent.AlertIssued || event is LiveEvent.AlertCleared

    companion object {
        /**
         * 64 frames is roughly 30 seconds of premium-cadence traffic across a full topic set.
         * Large enough to absorb a transient stall, small enough that a client which has been
         * unreachable for a minute gets cut rather than accumulating unbounded memory server-side.
         */
        const val DEFAULT_QUEUE_CAPACITY = 64
        private const val MAX_CRITICAL_STRIKES = 3
    }
}

object LiveCloseCodes {
    const val SLOW_CONSUMER: Short = 4008
    const val SERVER_SHUTDOWN: Short = 4503
}
