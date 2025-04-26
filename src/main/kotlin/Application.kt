package bose.ankush

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(factory = Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    configureMonitoring()
    configureHTTP()
    configureRouting()
}
