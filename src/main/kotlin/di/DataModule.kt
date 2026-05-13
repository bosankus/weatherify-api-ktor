package di

import com.androidplay.core.di.coreInfraModule
import com.androidplay.core.mongo.MongoConnection
import com.androidplay.weatherify.di.weatherifyModule
import com.androidplay.weatherify.di.weatherifyIndexes
import com.androidplay.core.mongo.MongoIndexer
import config.Environment
import data.source.WeatherApiClient
import data.source.WeatherApiClientImpl
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.androidplay.core.secrets.getSecretValue
import util.Constants
val dataModule = module {

    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                maxConnectionsCount = 100
                endpoint {
                    maxConnectionsPerRoute = 20
                    pipelineMaxSize = 20
                    keepAliveTime = 5000
                    connectTimeout = 5000
                    connectAttempts = 3
                }
            }
        }
    }

    single(named("razorpayJson")) {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            encodeDefaults = false
            coerceInputValues = true
        }
    }

    single<WeatherApiClient> { WeatherApiClientImpl() }

    single<util.Analytics> { util.GoogleAnalyticsClient.fromEnv() }
}

/** Returns the infra module (Mongo + Redis/cache + job queue) for weatherify-api. */
fun buildInfraModule() = run {
    val mongoUri = getSecretValue(Constants.Auth.DB_CONNECTION_STRING_SECRET)
    coreInfraModule(
        mongoUri = mongoUri,
        databaseName = Environment.getDbName(),
        redisUrl = getSecretValue("redis-url"),
        mongoSettings = MongoConnection.pooledSettings(
            uri = mongoUri,
            maxPoolSize = 50,
            minPoolSize = 5,
        ),
    )
}

/** Returns the weatherify repositories module (consuming MongoDatabase from infra module). */
fun buildWeatherifyModule() = weatherifyModule()

/** Ensures all weatherify MongoDB indexes after Koin starts. Call after startKoin. */
suspend fun ensureWeatherifyIndexes(db: com.mongodb.kotlin.client.coroutine.MongoDatabase) =
    MongoIndexer.ensure(db, weatherifyIndexes())
