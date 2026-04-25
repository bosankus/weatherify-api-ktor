package di

import data.service.*
import domain.service.*
import domain.service.impl.*
import domain.service.NominatimService
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
    single<WeatherAggregatorService> { WeatherAggregatorServiceImpl(get(), get()) }
    single<EmailService> { EmailServiceImpl(get()) }
    single<RefundService> { RefundServiceImpl(get(), get(), get(), get(), get()) }
    single<FinancialService> { FinancialServiceImpl(get(), get(), get(), get()) }
    single<BillService> { BillServiceImpl(get(), get(), get(), get()) }
    single<NotificationService> { NotificationServiceImpl() }
    single<ServiceCatalogService> { ServiceCatalogServiceImpl(get(), get()) }
    single { NominatimService(get()) }
    single { SavedLocationService(get(), get(), get()) }
    single { NoteService(get()) }

    // Data Services
    single { ServiceCatalogSeedingService(get()) }
    single { ServiceCatalogCache(get(), cacheDurationMinutes = 15) }
    single { data.service.PaymentAnalyticsCache(get(), get()) }

}
