package bose.ankush.route.common

import io.ktor.server.routing.Route

/**
 * Contract for registering feature routes in a decoupled way.
 * Implementations should only call existing Route extension functions
 * (e.g., homeRoute(), authRoute()) to avoid changing endpoint behavior.
 */
fun interface RouteRegistrar {
    fun register(root: Route)
}
