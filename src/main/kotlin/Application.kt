package bose.ankush

import bose.ankush.base.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

fun main() {
    val serverPort = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(
        factory = Netty,
        port = serverPort,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureDependencyInjection()
    configureMonitoring()
    configureHTTP()
    configureAuthentication()
    configureRouting()
    configureFirebase()
    configureServiceCatalogSeeding()
    configureServiceTypeResolver()
}

fun configureFirebase() {
    config.FirebaseAdmin.initialize()
}

fun Application.configureServiceCatalogSeeding() {
    val seedingService by inject<data.service.ServiceCatalogSeedingService>()
    monitor.subscribe(ApplicationStarted) {
        this.launch {
            seedingService.seedInitialServices()
        }
    }
}

fun Application.configureServiceTypeResolver() {
    val serviceCatalogRepository by inject<com.androidplay.weatherify.repository.ServiceCatalogRepository>()
    util.ServiceTypeResolver.initialize(serviceCatalogRepository)
}

