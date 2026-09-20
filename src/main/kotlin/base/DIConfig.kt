package bose.ankush.base

import di.appModule
import di.buildInfraModule
import di.buildWeatherifyModule
import di.ensureWeatherifyIndexes
import com.androidplay.core.cache.CacheRepository
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.koin.core.logger.Level
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

private val log = LoggerFactory.getLogger("DIConfig")

fun Application.configureDependencyInjection() {
    install(Koin) {
        slf4jLogger(level = Level.INFO)
        modules(appModule, buildInfraModule(), buildWeatherifyModule())
    }

    /**
     * Index creation runs *after* the server is listening, not inline here.
     *
     * Ktor executes this module before embeddedServer() binds the port, so a blocking call at
     * this point gates the port on MongoDB being reachable. When it is not, the driver spends its
     * full 30s server-selection timeout and throws, main() dies, and the platform reports only
     * "container failed to start and listen on port 8080" — the real cause (bad credentials,
     * unreachable cluster) never appears near the top of the logs.
     *
     * Creating an index that already exists is a no-op, so deferring costs nothing, and the few
     * requests that can arrive before it completes are served by a collection that has carried
     * these indexes since the previous deploy.
     */
    val db: MongoDatabase by inject()
    monitor.subscribe(ApplicationStarted) {
        it.launch {
            runCatching { ensureWeatherifyIndexes(db) }
                .onFailure { error -> log.error("Failed to ensure Weatherify indexes", error) }
        }
    }

    val cacheRepository: CacheRepository by inject()
    monitor.subscribe(ApplicationStopped) {
        cacheRepository.close()
    }
}
