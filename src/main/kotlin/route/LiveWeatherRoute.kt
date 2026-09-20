package bose.ankush.route

import domain.model.live.ClientCommand
import domain.model.live.LiveChannel
import domain.model.live.LiveErrorCode
import domain.model.live.LocationRef
import domain.model.live.PROTOCOL_VERSION
import domain.model.live.ServerMessage
import domain.model.live.TopicAck
import domain.model.live.TopicRejection
import domain.service.SavedLocationService
import domain.service.live.LiveConnection
import domain.service.live.LiveEntitlementResolver
import domain.service.live.LiveHub
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.Constants
import util.LiveTopic
import java.util.UUID

/**
 * JSON codec for the live protocol.
 *
 * ignoreUnknownKeys is mandatory, not a convenience: a v1.2 client will send fields a v1.0
 * server has never heard of, and rejecting the frame would break forward compatibility.
 *
 * encodeDefaults must be true so the protocol version travels on every frame even when it equals
 * the default — a client cannot negotiate against a field that was omitted.
 */
private val liveJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = false
    classDiscriminator = "type"
}

private const val LIVE_ENDPOINT = "/ws/live"

/**
 * Real-time weather endpoint.
 *
 * Handshake is a normal authenticated HTTP GET carrying `Upgrade: websocket`, so the existing
 * "jwt-auth" provider secures it exactly like every REST route: a rejected token produces a plain
 * 401 *before* the protocol upgrade, which is far easier for a client to handle than an upgrade
 * that succeeds and then immediately closes.
 */
fun Route.liveWeatherRoute() {
    val log = LoggerFactory.getLogger("LiveWeatherRoute")
    val hub: LiveHub by application.inject()
    val entitlementResolver: LiveEntitlementResolver by application.inject()
    val savedLocationService: SavedLocationService by application.inject()

    authenticate("jwt-auth") {
        webSocket(LIVE_ENDPOINT) {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)?.asString()

            if (email.isNullOrBlank()) {
                // Belt and braces: the auth provider should already have rejected this.
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "missing email claim"))
                return@webSocket
            }

            val entitlements = entitlementResolver.resolve(email)
            val connection = LiveConnection(
                id = UUID.randomUUID().toString(),
                email = email,
                entitlements = entitlements
            )

            log.info(
                "WS open id={} user={} tier={} cadence={}s",
                connection.id, email, entitlements.tier, entitlements.cadenceSeconds
            )

            // ---------------------------------------------------------------
            // Writer coroutine
            //
            // The only place in the entire feature that touches the socket's outgoing side.
            // Fan-out hands frames to connection.queue and returns immediately; this coroutine
            // drains at whatever rate the client's TCP window allows. That separation is what
            // keeps one slow phone from stalling every other subscriber on the same topic.
            // ---------------------------------------------------------------
            val writer = launch {
                try {
                    connection.queue.consumeEach { message ->
                        send(Frame.Text(liveJson.encodeToString(ServerMessage.serializer(), message)))
                    }
                } catch (e: Exception) {
                    log.debug("Writer for {} ended: {}", connection.id, e.message)
                }
            }

            try {
                connection.offer(
                    ServerMessage.Hello(
                        connectionId = connection.id,
                        serverTime = System.currentTimeMillis(),
                        tier = entitlements.tier,
                        allowedChannels = entitlements.allowedChannels,
                        cadenceSeconds = entitlements.cadenceSeconds,
                        maxTopics = entitlements.maxTopics,
                        heartbeatSeconds = HEARTBEAT_SECONDS
                    )
                )

                // -----------------------------------------------------------
                // Reader loop
                //
                // `incoming` yields only after the Ktor WebSockets plugin has handled the
                // protocol-level frames (ping/pong/close) for us, so everything arriving here is
                // application traffic.
                // -----------------------------------------------------------
                for (frame in incoming) {
                    if (!this.isActive || connection.closeRequested) break

                    when (frame) {
                        is Frame.Text -> handleTextFrame(
                            raw = frame.readText(),
                            connection = connection,
                            hub = hub,
                            savedLocationService = savedLocationService
                        )

                        is Frame.Binary -> connection.offer(
                            ServerMessage.Error(
                                code = LiveErrorCode.BAD_FRAME,
                                message = "Binary frames are not supported on this endpoint"
                            )
                        )

                        else -> Unit // Close/Ping/Pong are handled by the plugin
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                log.debug("WS {} closed by peer", connection.id)
            } catch (e: Exception) {
                log.warn("WS {} failed: {}", connection.id, e.message)
            } finally {
                // Runs on every exit path — normal close, network drop, server shutdown, or an
                // exception above. Leaking a subscription here would keep a dead connection's
                // poller alive forever, so this must be unconditional.
                hub.removeConnection(connection)
                connection.requestClose(connection.closeCode)
                writer.cancel()
                log.info(
                    "WS closed id={} user={} dropped={}",
                    connection.id, email, connection.droppedFrames.get()
                )
            }
        }
    }
}

private const val HEARTBEAT_SECONDS = 30

private suspend fun handleTextFrame(
    raw: String,
    connection: LiveConnection,
    hub: LiveHub,
    savedLocationService: SavedLocationService
) {
    val command = try {
        liveJson.decodeFromString(ClientCommand.serializer(), raw)
    } catch (e: Exception) {
        // A malformed frame is a client bug, not a reason to drop the connection. Reply with a
        // typed error and keep going, so one bad frame never costs the user their live session.
        connection.offer(
            ServerMessage.Error(
                code = LiveErrorCode.BAD_FRAME,
                message = "Could not parse frame: ${e.message?.take(200)}"
            )
        )
        return
    }

    when (command) {
        is ClientCommand.Ping -> connection.offer(
            ServerMessage.Pong(
                cid = command.cid,
                serverTime = System.currentTimeMillis(),
                clientTime = command.clientTime
            )
        )

        is ClientCommand.Subscribe -> doSubscribe(
            connection = connection,
            hub = hub,
            cid = command.cid,
            refs = command.locations,
            requestedChannels = command.channels,
            cursor = command.cursor
        )

        is ClientCommand.SubscribeSaved -> {
            val saved = savedLocationService.getSavedLocations(connection.email).getOrNull()
            if (saved == null) {
                connection.offer(
                    ServerMessage.Error(
                        cid = command.cid,
                        code = LiveErrorCode.INTERNAL,
                        message = "Could not load saved locations"
                    )
                )
                return
            }
            doSubscribe(
                connection = connection,
                hub = hub,
                cid = command.cid,
                refs = saved.map { LocationRef(lat = it.lat, lon = it.lon, label = it.name) },
                requestedChannels = command.channels,
                cursor = command.cursor
            )
        }

        is ClientCommand.Unsubscribe -> {
            val removed = command.topics.filter { it in connection.topics }
            removed.forEach { hub.unsubscribe(connection, it) }
            connection.offer(ServerMessage.Unsubscribed(cid = command.cid, topics = removed))
        }
    }
}

private fun doSubscribe(
    connection: LiveConnection,
    hub: LiveHub,
    cid: String?,
    refs: List<LocationRef>,
    requestedChannels: List<String>,
    cursor: Map<String, Long>
) {
    // Channel negotiation: the client asks, the server decides. Asking for a channel the tier
    // does not include is not an error — it is silently narrowed, and the resulting set is echoed
    // back so the client knows what it actually got.
    if (requestedChannels.isNotEmpty()) {
        val granted = requestedChannels
            .filter { it in LiveChannel.ALL && connection.entitlements.allows(it) }
            .toSet()
        if (granted.isNotEmpty()) connection.channels = granted
    }

    val accepted = mutableListOf<TopicAck>()
    val rejected = mutableListOf<TopicRejection>()

    for (ref in refs) {
        if (!LiveTopic.validCoordinates(ref.lat, ref.lon)) {
            rejected += TopicRejection(
                label = ref.label, lat = ref.lat, lon = ref.lon,
                code = LiveErrorCode.INVALID_COORDINATES,
                message = "Latitude must be -90..90 and longitude -180..180"
            )
            continue
        }

        // Per-tier ceiling on topics. Without it a single client could pin thousands of pollers
        // and turn one account into an upstream-quota denial of service.
        val prospective = LiveTopic.forCoordinates(ref.lat, ref.lon)
        if (prospective !in connection.topics &&
            connection.topics.size >= connection.entitlements.maxTopics
        ) {
            rejected += TopicRejection(
                label = ref.label, lat = ref.lat, lon = ref.lon,
                code = LiveErrorCode.TOPIC_LIMIT_EXCEEDED,
                message = "This plan allows ${connection.entitlements.maxTopics} live locations"
            )
            continue
        }

        accepted += hub.subscribe(connection, ref, cursor[prospective])
    }

    connection.offer(
        ServerMessage.Subscribed(cid = cid, topics = accepted, rejected = rejected)
    )
}

/** Exposed so the registrar and docs share one definition of the path. */
object LiveWeatherEndpoint {
    const val PATH = LIVE_ENDPOINT
    const val VERSION = PROTOCOL_VERSION
}
