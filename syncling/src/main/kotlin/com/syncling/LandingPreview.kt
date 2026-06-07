package com.syncling

import com.syncling.routes.landingPage
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Local-only landing preview. Boots a tiny Ktor server on PREVIEW_PORT
 * (default 8082) that mounts the real landingPage() with no host check
 * and no DB/Redis dependencies, so designers can hit it in the browser
 * without standing up the full stack.
 *
 *   ./gradlew :syncling:previewLanding
 *   open http://localhost:8082
 */
fun main() {
    val port = System.getenv("PREVIEW_PORT")?.toIntOrNull() ?: 8082
    println("→ syncling landing preview at http://localhost:$port")
    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        routing {
            get("/") { call.respondHtml { landingPage() } }
            get("/{...}") { call.respondRedirect("/") }
        }
    }.start(wait = true)
}
