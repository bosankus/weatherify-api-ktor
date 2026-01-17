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

    routing {
        // Serve favicon at root and common admin paths
        get("/favicon.ico") {
            call.respondText("Greetings! Favicon is currently dry or hidden 😉. Please hydrate elsewhere.")
        }

        // Handle favicon requests from admin dashboard paths
        get("/admin/dashboard/favicon.ico") {
            call.respondText("Greetings! Favicon is currently dry or hidden 😉. Please hydrate elsewhere.")
        }

        // Register all application routes via registrars (order preserved by Koin list)
        registrars.forEach { it.register(this) }

        // /login is now handled by AdminAuthRoute.kt, no redirect needed

        // Register the 404 error page route
        notFoundRoute()

        // Catch-all routes for handling non-existing endpoints with common HTTP methods
        // These should be placed AFTER all specific routes to avoid conflicts

        // Helper function to check if path should be excluded from 404 handling
        fun shouldExcludeFrom404(path: String): Boolean {
            val excludedPaths = setOf(
                "/not-found",
                "/favicon.ico",
                "/",
                "/weather",
                "/air-pollution",
                "/feedback",
                "/login",
                "/register",
                "/refresh-token",
                "/wfy/terms-and-conditions",
                "/wfy/privacy-policy",
                "/dashboard",
                "/finance",
                "/tools",
                "/users",
                "/services",
                "/refunds",
                "/cache"
            )

            // Check for exact matches first
            if (excludedPaths.contains(path)) {
                return true
            }

            return false
        }

        // GET catch-all (exclude specific paths to prevent redirect loop)
        get("{...}") {
            val path = call.request.path()
            // Skip if this is an excluded path to prevent infinite redirect
            if (!shouldExcludeFrom404(path)) {
                logger.info("404 Not Found: GET request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }

        // POST catch-all
        post("{...}") {
            val path = call.request.path()
            if (!shouldExcludeFrom404(path)) {
                logger.info("404 Not Found: POST request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }

        // PUT catch-all
        put("{...}") {
            val path = call.request.path()
            if (!shouldExcludeFrom404(path)) {
                logger.info("404 Not Found: PUT request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }

        // DELETE catch-all
        delete("{...}") {
            val path = call.request.path()
            if (!shouldExcludeFrom404(path)) {
                logger.info("404 Not Found: DELETE request to non-existent endpoint: $path")
                call.handleNotFound()
            }
        }
    }
}
