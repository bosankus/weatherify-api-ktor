package domain.model.live

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Wire protocol for the /ws/live endpoint.
 *
 * Design rules (see docs/live-weather-websocket.md):
 *  - every frame is a JSON object carrying a "type" discriminator
 *  - every frame carries "v", the protocol version, so old mobile clients keep working
 *  - client requests carry a client-generated "cid" which the matching reply echoes back
 *  - events carry a per-topic monotonic "seq" so a reconnecting client can detect gaps
 *
 * The protocol is deliberately additive-only: new event kinds and new optional fields may be
 * added within v1. Removing or renaming a field requires bumping PROTOCOL_VERSION.
 */
const val PROTOCOL_VERSION = 1

// ---------------------------------------------------------------------------
// Client -> Server
// ---------------------------------------------------------------------------

@Serializable
@JsonClassDiscriminator("type")
@OptIn(ExperimentalSerializationApi::class)
sealed class ClientCommand {
    abstract val cid: String?

    /**
     * Subscribe to explicit coordinates. Each entry is snapped to a geohash cell server-side,
     * so two clients 200m apart share one upstream poller.
     */
    @Serializable
    @SerialName("subscribe")
    data class Subscribe(
        override val cid: String? = null,
        val locations: List<LocationRef> = emptyList(),
        /** Channels the client wants. Server intersects this with the user's entitlements. */
        val channels: List<String> = emptyList(),
        /** Last seq the client already processed, per topic. Used to detect a gap after reconnect. */
        val cursor: Map<String, Long> = emptyMap()
    ) : ClientCommand()

    /**
     * Subscribe to everything in the caller's saved_locations collection.
     * Saves the app from having to send the list it already synced over REST.
     */
    @Serializable
    @SerialName("subscribe_saved")
    data class SubscribeSaved(
        override val cid: String? = null,
        val channels: List<String> = emptyList(),
        val cursor: Map<String, Long> = emptyMap()
    ) : ClientCommand()

    @Serializable
    @SerialName("unsubscribe")
    data class Unsubscribe(
        override val cid: String? = null,
        val topics: List<String> = emptyList()
    ) : ClientCommand()

    /**
     * Application-level ping. The transport already has RFC 6455 ping/pong frames, but those are
     * answered by the OS/library layer and therefore prove only that the socket is open, not that
     * the server coroutine is still alive and draining. This one proves liveness end to end.
     */
    @Serializable
    @SerialName("ping")
    data class Ping(
        override val cid: String? = null,
        val clientTime: Long? = null
    ) : ClientCommand()
}

@Serializable
data class LocationRef(
    val lat: Double,
    val lon: Double,
    /** Optional display label echoed back on the subscription ack, purely for client convenience. */
    val label: String? = null
)

// ---------------------------------------------------------------------------
// Server -> Client
// ---------------------------------------------------------------------------

@Serializable
@JsonClassDiscriminator("type")
@OptIn(ExperimentalSerializationApi::class)
sealed class ServerMessage {
    abstract val v: Int

    /**
     * First frame on every connection. Tells the client what it is allowed to do and how the
     * server expects it to behave, so none of that has to be hardcoded in the app.
     */
    @Serializable
    @SerialName("hello")
    data class Hello(
        override val v: Int = PROTOCOL_VERSION,
        val connectionId: String,
        val serverTime: Long,
        /** Entitlement tier resolved from the user's subscription. */
        val tier: String,
        /** Channels this user may subscribe to. */
        val allowedChannels: List<String>,
        /** How often this tier receives conditions updates. */
        val cadenceSeconds: Int,
        /** Max topics this connection may hold. */
        val maxTopics: Int,
        /** Client should send an app-level ping if it has been silent this long. */
        val heartbeatSeconds: Int
    ) : ServerMessage()

    @Serializable
    @SerialName("subscribed")
    data class Subscribed(
        override val v: Int = PROTOCOL_VERSION,
        val cid: String? = null,
        val topics: List<TopicAck> = emptyList(),
        val rejected: List<TopicRejection> = emptyList()
    ) : ServerMessage()

    @Serializable
    @SerialName("unsubscribed")
    data class Unsubscribed(
        override val v: Int = PROTOCOL_VERSION,
        val cid: String? = null,
        val topics: List<String> = emptyList()
    ) : ServerMessage()

    /** A domain event on a topic the connection is subscribed to. */
    @Serializable
    @SerialName("event")
    data class Event(
        override val v: Int = PROTOCOL_VERSION,
        val topic: String,
        val seq: Long,
        val ts: Long,
        val event: LiveEvent
    ) : ServerMessage()

    @Serializable
    @SerialName("pong")
    data class Pong(
        override val v: Int = PROTOCOL_VERSION,
        val cid: String? = null,
        val serverTime: Long,
        val clientTime: Long? = null
    ) : ServerMessage()

    /**
     * A recoverable, per-request error. The connection stays open. Fatal problems close the
     * socket with a close code instead (see LiveCloseReason).
     */
    @Serializable
    @SerialName("error")
    data class Error(
        override val v: Int = PROTOCOL_VERSION,
        val cid: String? = null,
        val code: String,
        val message: String
    ) : ServerMessage()
}

@Serializable
data class TopicAck(
    val topic: String,
    val label: String? = null,
    val lat: Double,
    val lon: Double,
    /** Current server-side sequence for this topic. */
    val seq: Long,
    /**
     * True when the client's cursor was too far behind to be reconciled from events alone,
     * so it must treat the next snapshot as a full reset rather than a delta.
     */
    val gap: Boolean = false
)

@Serializable
data class TopicRejection(
    val label: String? = null,
    val lat: Double,
    val lon: Double,
    val code: String,
    val message: String
)

/** Error codes sent in ServerMessage.Error. Stable strings — the client switches on these. */
object LiveErrorCode {
    const val BAD_FRAME = "BAD_FRAME"
    const val UNKNOWN_COMMAND = "UNKNOWN_COMMAND"
    const val TOPIC_LIMIT_EXCEEDED = "TOPIC_LIMIT_EXCEEDED"
    const val CHANNEL_FORBIDDEN = "CHANNEL_FORBIDDEN"
    const val INVALID_COORDINATES = "INVALID_COORDINATES"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val INTERNAL = "INTERNAL"
}

/**
 * Close codes. 1000-2999 are reserved by RFC 6455; 4000-4999 are free for applications.
 * The client's reconnect policy keys off these: 4401 means "get a new token first",
 * 4429 means "back off hard", 1011 means "retry with normal backoff".
 */
object LiveCloseReason {
    const val UNAUTHORIZED = 4401
    const val TOKEN_EXPIRED = 4403
    const val RATE_LIMITED = 4429
    const val PROTOCOL_VERSION_UNSUPPORTED = 4400
    const val SERVER_SHUTDOWN = 4503
}
