package di

import data.repository.FeedbackRepositoryImpl
import data.repository.PaymentRepositoryImpl
import data.repository.RefundRepositoryImpl
import data.repository.UserRepositoryImpl
import data.repository.WeatherRepositoryImpl
import data.source.DatabaseModule
import data.source.WeatherApiClient
import data.source.WeatherApiClientImpl
import domain.repository.FeedbackRepository
import domain.repository.PaymentRepository
import domain.repository.RefundRepository
import domain.repository.UserRepository
import domain.repository.WeatherRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for data layer dependencies.
 * This module provides database, API client, and repository implementations.
 */
val dataModule = module {
    // Database
    single { DatabaseModule() }

    // HTTP Client for external API calls (email, etc.)
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

    // Shared JSON configuration for Razorpay API
    single(named("razorpayJson")) {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            encodeDefaults = false
            coerceInputValues = true
        }
    }

    // API Clients
    single<WeatherApiClient> { WeatherApiClientImpl() }

    // Analytics
    single<util.Analytics> { util.GoogleAnalyticsClient.fromEnv() }

    // Repositories
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<FeedbackRepository> { FeedbackRepositoryImpl(get()) }
    single<WeatherRepository> { WeatherRepositoryImpl(get(), get()) }
    single<PaymentRepository> { PaymentRepositoryImpl(get()) }
    single<RefundRepository> { RefundRepositoryImpl(get()) }
    single<domain.repository.ServiceCatalogRepository> { data.repository.ServiceCatalogRepositoryImpl(get()) }
}
