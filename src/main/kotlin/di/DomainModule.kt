package di

import data.service.BillServiceImpl
import data.service.EmailServiceImpl
import data.service.FinancialServiceImpl
import data.service.SubscriptionNotificationServiceImpl
import data.service.SubscriptionServiceImpl
import domain.service.AuthService
import domain.service.BillService
import domain.service.EmailService
import domain.service.FeedbackService
import domain.service.FinancialService
import domain.service.NotificationService
import domain.service.NotificationServiceImpl
import domain.service.RefundService
import domain.service.SubscriptionNotificationService
import domain.service.SubscriptionService
import domain.service.WeatherService
import domain.service.impl.AuthServiceImpl
import domain.service.impl.FeedbackServiceImpl
import domain.service.impl.RefundServiceImpl
import domain.service.impl.WeatherServiceImpl
import org.koin.dsl.module
import util.SubscriptionExpirationJob

/**
 * Koin module for domain layer dependencies.
 * This module provides service implementations.
 */
val domainModule = module {
    // Services
    single<AuthService> { AuthServiceImpl(get()) }
    single<FeedbackService> { FeedbackServiceImpl(get()) }
    single<WeatherService> { WeatherServiceImpl(get()) }
    single<SubscriptionService> { SubscriptionServiceImpl(get(), get(), get()) }
    single<SubscriptionNotificationService> { SubscriptionNotificationServiceImpl(get()) }
    single<EmailService> { EmailServiceImpl(get()) }
    single<RefundService> { RefundServiceImpl(get(), get(), get(), get(), get()) }
    single<FinancialService> { FinancialServiceImpl(get(), get(), get()) }
    single<BillService> { BillServiceImpl(get(), get(), get()) }
    single<NotificationService> { NotificationServiceImpl() }
    single<domain.service.ServiceCatalogService> { domain.service.impl.ServiceCatalogServiceImpl(get(), get(), get()) }

    // Data Services
    single { data.service.ServiceCatalogSeedingService(get()) }
    single { data.service.ServiceCatalogCache(get(), cacheDurationMinutes = 15) }

    // Background Jobs
    single { SubscriptionExpirationJob(get(), get()) }
}
