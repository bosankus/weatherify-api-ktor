package bose.ankush.base

import bose.ankush.route.common.RouteRegistrar
import bose.ankush.route.handleNotFound
import bose.ankush.route.notFoundRoute
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")
    val registrars by inject<List<RouteRegistrar>>()

    val excluded404Paths = setOf(
        "/not-found", "/favicon.ico", "/"
    )

    routing {
        get("/favicon.ico") {
            call.respondText("Greetings! You have reached a dead end, I won't say where you must go. Decide yourself")
        }
        get("/admin/dashboard/favicon.ico") {
            call.respondText("Greetings! You have reached a dead end, I won't say where you must go. Decide yourself")
        }

        registrars.forEach { it.register(this) }
        notFoundRoute()

        get("{...}") {
            val path = call.request.path()
            if (path !in excluded404Paths) {
                logger.info("404 Not Found: GET request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }
        post("{...}") {
            val path = call.request.path()
            if (path !in excluded404Paths) {
                logger.info("404 Not Found: POST request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }
        put("{...}") {
            val path = call.request.path()
            if (path !in excluded404Paths) {
                logger.info("404 Not Found: PUT request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }
        delete("{...}") {
            val path = call.request.path()
            if (path !in excluded404Paths) {
                logger.info("404 Not Found: DELETE request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }
    }
}
