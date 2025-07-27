package bose.ankush

import bose.ankush.base.configureAuthentication
import bose.ankush.base.configureDependencyInjection
import bose.ankush.base.configureHTTP
import bose.ankush.base.configureMonitoring
import bose.ankush.base.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(factory = Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    configureDependencyInjection()
    configureMonitoring()
    configureHTTP()
    configureAuthentication()
    configureRouting()
}
