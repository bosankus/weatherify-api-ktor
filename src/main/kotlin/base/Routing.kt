package bose.ankush.base

import bose.ankush.route.adminRoute
import bose.ankush.route.authRoute
import bose.ankush.route.feedbackRoute
import bose.ankush.route.handleNotFound
import bose.ankush.route.homeRoute
import bose.ankush.route.notFoundRoute
import bose.ankush.route.privacyPolicyRoute
import bose.ankush.route.termsAndConditionsRoute
import bose.ankush.route.weatherRoute
import io.ktor.server.application.Application
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")

    routing {
        // Register all application routes
        homeRoute()
        weatherRoute()
        feedbackRoute()
        authRoute()
        adminRoute()
        termsAndConditionsRoute()
        privacyPolicyRoute()

        // Register the 404 error page route
        notFoundRoute()

        // Catch-all routes for handling non-existing endpoints with common HTTP methods

        // GET catch-all
        get("{...}") {
            logger.info("404 Not Found: GET request to non-existent endpoint")
            call.handleNotFound()
        }

        // POST catch-all
        post("{...}") {
            logger.info("404 Not Found: POST request to non-existent endpoint")
            call.handleNotFound()
        }

        // PUT catch-all
        put("{...}") {
            logger.info("404 Not Found: PUT request to non-existent endpoint")
            call.handleNotFound()
        }

        // DELETE catch-all
        delete("{...}") {
            logger.info("404 Not Found: DELETE request to non-existent endpoint")
            call.handleNotFound()
        }
    }
}
