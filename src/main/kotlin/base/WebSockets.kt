package bose.ankush.base

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.seconds

/**
 * WebSocket transport configuration.
 *
 * These four numbers are the difference between a connection that survives a real mobile network
 * and one that quietly dies, so each is deliberate rather than copied from the docs.
 */
fun Application.configureWebSockets() {
    install(WebSockets) {
        /**
         * The server sends an RFC 6455 ping every 20s.
         *
         * This is not primarily about detecting dead clients — it is about *keeping the path
         * open*. Carrier NATs and cloud load balancers reclaim idle TCP flows aggressively,
         * often at 60s, and they do it silently: neither end is told. The socket then looks open
         * to both sides while being a black hole. Periodic traffic keeps the mapping alive.
         *
         * 20s is chosen to sit comfortably under the 60s idle timeouts common in GCP/AWS L7
         * load balancers, with room for one ping to be lost.
         */
        pingPeriod = 20.seconds

        /**
         * If a ping goes unanswered for 45s the connection is closed and the hub reclaims its
         * subscriptions. Roughly two missed pings — long enough to ride out a brief cell handover,
         * short enough that a phone which went into a tunnel does not hold a poller open for
         * minutes.
         */
        timeout = 45.seconds

        /**
         * Inbound frame cap. Client commands are a few hundred bytes; 64 KB is already absurdly
         * generous. The point is that this is an *unauthenticated-until-parsed* input path, and
         * without a cap a single client could stream a multi-gigabyte frame and exhaust heap.
         */
        maxFrameSize = 64L * 1024

        /**
         * Note on compression: Ktor's permessage-deflate support is an opt-in extension
         * (WebSocketDeflateExtension) and we deliberately do not install it. Deflate keeps a
         * compression context per connection — roughly 30-300 KB of native memory each depending
         * on window size. At 10k connections that is gigabytes spent compressing ~120-byte
         * payloads where the frame header already dominates. Compression pays for large
         * documents, not for telemetry.
         */
    }
}
