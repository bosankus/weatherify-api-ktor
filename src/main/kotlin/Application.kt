package bose.ankush

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(factory = Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    configureMonitoring()
    configureHTTP()
    configureAuthentication()
    configureRouting()
}
