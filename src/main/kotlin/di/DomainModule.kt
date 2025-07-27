package di

import domain.service.AuthService
import domain.service.FeedbackService
import domain.service.WeatherService
import domain.service.impl.AuthServiceImpl
import domain.service.impl.FeedbackServiceImpl
import domain.service.impl.WeatherServiceImpl
import org.koin.dsl.module

/**
 * Koin module for domain layer dependencies.
 * This module provides service implementations.
 */
val domainModule = module {
    // Services
    single<AuthService> { AuthServiceImpl(get()) }
    single<FeedbackService> { FeedbackServiceImpl(get()) }
    single<WeatherService> { WeatherServiceImpl(get()) }
}