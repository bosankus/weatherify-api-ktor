package bose.ankush.base

import di.appModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.core.logger.Level
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * Configure dependency injection using Koin.
 * This function installs the Koin Ktor feature and loads the application modules.
 */
fun Application.configureDependencyInjection() {
    install(Koin) {
        slf4jLogger(level = Level.INFO)
        modules(appModule)
    }
}
