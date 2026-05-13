package bose.ankush.base

import di.appModule
import di.buildInfraModule
import di.buildWeatherifyModule
import di.ensureWeatherifyIndexes
import com.androidplay.core.cache.CacheRepository
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import kotlinx.coroutines.runBlocking
import org.koin.core.logger.Level
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDependencyInjection() {
    install(Koin) {
        slf4jLogger(level = Level.INFO)
        modules(appModule, buildInfraModule(), buildWeatherifyModule())
    }

    val db: MongoDatabase by inject()
    runBlocking { ensureWeatherifyIndexes(db) }

    val cacheRepository: CacheRepository by inject()
    environment.monitor.subscribe(ApplicationStopped) {
        cacheRepository.close()
    }
}
