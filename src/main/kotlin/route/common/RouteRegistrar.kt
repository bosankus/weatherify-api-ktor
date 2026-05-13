package bose.ankush.route.common

import io.ktor.server.routing.Route

fun interface RouteRegistrar {
    fun register(root: Route)
}
