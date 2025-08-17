package bose.ankush.base

import bose.ankush.route.common.RouteRegistrar
import bose.ankush.route.handleNotFound
import bose.ankush.route.notFoundRoute
import io.ktor.server.application.Application
import io.ktor.server.request.path
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")
    val registrars by inject<List<RouteRegistrar>>()

    routing {
        // Serve favicon
        get("/favicon.ico") {
            call.respondText("Greetings! Favicon is currently dry or hidden 😉. Please hydrate elsewhere.")
        }

        // Register all application routes via registrars (order preserved by Koin list)
        registrars.forEach { it.register(this) }

        // Handle login.html redirects to admin login
        get("/login.html") {
            // Get the error parameter if it exists
            val errorParam = call.request.queryParameters["error"]

            // Redirect to admin login with the error parameter if it exists
            if (errorParam != null) {
                logger.info("Redirecting /login.html?error=$errorParam to /admin/login?error=$errorParam")
                call.respondRedirect("/admin/login?error=$errorParam")
            } else {
                logger.info("Redirecting /login.html to /admin/login")
                call.respondRedirect("/admin/login")
            }
        }

        // Handle /login redirects to admin login
        get("/login") {
            // Get the error parameter if it exists
            val errorParam = call.request.queryParameters["error"]

            // Redirect to admin login with the error parameter if it exists
            if (errorParam != null) {
                logger.info("Redirecting /login?error=$errorParam to /admin/login?error=$errorParam")
                call.respondRedirect("/admin/login?error=$errorParam")
            } else {
                logger.info("Redirecting /login to /admin/login")
                call.respondRedirect("/admin/login")
            }
        }

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
                "/admin",
                "/admin/login"
            )

            // Check for exact matches first
            if (excludedPaths.contains(path)) {
                return true
            }

            // Check for admin sub-paths
            if (path.startsWith("/admin/")) {
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
