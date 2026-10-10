package bose.ankush.base

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin

/**
 * Number of trusted reverse proxies in front of the app, each of which appends the address of
 * its peer to `X-Forwarded-For`. Cloud Run (the default deployment) is one hop.
 */
private val trustedProxyHops: Int =
    System.getenv("TRUSTED_PROXY_HOPS")?.toIntOrNull()?.coerceAtLeast(1) ?: 1

/**
 * Resolves the real client address from `X-Forwarded-For`.
 *
 * A client can put anything in the leftmost entries, so those are never trusted. Entries are
 * counted from the right, where the infrastructure appends them: with [trustedHops] proxies in
 * front of the app, the client address is the [trustedHops]-th entry from the right.
 */
internal fun resolveClientIp(forwardedFor: String?, remoteHost: String, trustedHops: Int): String {
    val entries = forwardedFor?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    if (entries.isEmpty()) return remoteHost
    return entries[(entries.size - trustedHops).coerceAtLeast(0)]
}

/** Spoof-resistant client address, used as the key for every rate limiter. */
fun ApplicationCall.clientIp(): String =
    resolveClientIp(request.headers["X-Forwarded-For"], request.origin.remoteHost, trustedProxyHops)
