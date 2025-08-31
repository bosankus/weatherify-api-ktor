package di

import data.repository.FeedbackRepositoryImpl
import data.repository.UserRepositoryImpl
import data.repository.WeatherRepositoryImpl
import data.source.DatabaseModule
import data.source.WeatherApiClient
import data.source.WeatherApiClientImpl
import domain.repository.FeedbackRepository
import domain.repository.UserRepository
import domain.repository.WeatherRepository
import org.koin.dsl.module

/**
 * Koin module for data layer dependencies.
 * This module provides database, API client, and repository implementations.
 */
val dataModule = module {
    // Database
    single { DatabaseModule() }

    // API Clients
    single<WeatherApiClient> { WeatherApiClientImpl() }

    // Analytics
    single<util.Analytics> { util.GoogleAnalyticsClient.fromEnv() }

    // Repositories
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<FeedbackRepository> { FeedbackRepositoryImpl(get()) }
    single<WeatherRepository> { WeatherRepositoryImpl(get(), get()) }
}
