package di

import data.service.*
import domain.service.*
import domain.service.impl.AuthServiceImpl
import domain.service.impl.FeedbackServiceImpl
import domain.service.impl.RefundServiceImpl
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
    single<EmailService> { EmailServiceImpl(get()) }
    single<RefundService> { RefundServiceImpl(get(), get(), get(), get(), get()) }
    single<FinancialService> { FinancialServiceImpl(get(), get(), get()) }
    single<BillService> { BillServiceImpl(get(), get(), get(), get()) }
    single<NotificationService> { NotificationServiceImpl() }
    single<ServiceCatalogService> { domain.service.impl.ServiceCatalogServiceImpl(get(), get()) }

    // Data Services
    single { ServiceCatalogSeedingService(get()) }
    single { ServiceCatalogCache(get(), cacheDurationMinutes = 15) }

}
