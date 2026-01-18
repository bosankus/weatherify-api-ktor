package bose.ankush

import bose.ankush.base.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

fun main() {
    embeddedServer(factory = Netty, port = 8080, module = Application::module).start(wait = true)
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
    configureDatabaseIndexes()
}

/**
 * Configure Firebase Admin SDK for push notifications.
 * This function initializes Firebase during application startup.
 */
fun configureFirebase() {
    config.FirebaseAdmin.initialize()
}

/**
 * Configure service catalog seeding.
 * This function seeds the database with initial service catalog data on application startup.
 */
fun Application.configureServiceCatalogSeeding() {
    val seedingService by inject<data.service.ServiceCatalogSeedingService>()

    // Run seeding asynchronously on application startup (non-blocking)
    monitor.subscribe(ApplicationStarted) {
        this.launch {
            seedingService.seedInitialServices()
        }
    }
}

/**
 * Configure ServiceTypeResolver for backward compatibility.
 * This function initializes the resolver with the ServiceCatalogRepository.
 */
fun Application.configureServiceTypeResolver() {
    val serviceCatalogRepository by inject<domain.repository.ServiceCatalogRepository>()

    // Initialize the resolver after DI is configured
    util.ServiceTypeResolver.initialize(serviceCatalogRepository)
}

/**
 * Configure database indexes asynchronously during application startup.
 * This replaces runBlocking in init blocks for better startup performance.
 */
fun Application.configureDatabaseIndexes() {
    val databaseModule by inject<data.source.DatabaseModule>()

    // Create indexes asynchronously on application startup
    monitor.subscribe(ApplicationStarted) {
        this.launch {
            databaseModule.createIndexes()
        }
    }
}
