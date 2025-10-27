package di

import bose.ankush.route.common.*
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Provides ordered route registrars so features can register routes decoupled from the central router.
 * The order preserves existing behavior.
 */
val routeModule = module {
    // Bind each registrar with a qualifier
    single<RouteRegistrar>(named("home")) { HomeRoutesRegistrar }
    single<RouteRegistrar>(named("weather")) { WeatherRoutesRegistrar }
    single<RouteRegistrar>(named("feedback")) { FeedbackRoutesRegistrar }
    single<RouteRegistrar>(named("auth")) { AuthRoutesRegistrar }
    single<RouteRegistrar>(named("adminAuth")) { AdminAuthRoutesRegistrar }
    single<RouteRegistrar>(named("users")) { UserRoutesRegistrar }
    single<RouteRegistrar>(named("terms")) { TermsAndConditionsRoutesRegistrar }
    single<RouteRegistrar>(named("privacy")) { PrivacyPolicyRoutesRegistrar }
    single<RouteRegistrar>(named("payments")) { PaymentRoutesRegistrar }
    single<RouteRegistrar>(named("subscriptions")) { SubscriptionRoutesRegistrar }
    single<RouteRegistrar>(named("refunds")) { RefundRoutesRegistrar }
    single<RouteRegistrar>(named("serviceCatalog")) { ServiceCatalogRoutesRegistrar }
    single<RouteRegistrar>(named("mock")) { MockRoutesRegistrar }
    single<RouteRegistrar>(named("decode")) { DecodeRoutesRegistrar }
    single<RouteRegistrar>(named("pollingengine")) { PollingEngineRoutesRegistrar }

    // Provide ordered list
    single<List<RouteRegistrar>> {
        listOf(
            get(named("home")),
            get(named("weather")),
            get(named("feedback")),
            get(named("auth")),
            get(named("adminAuth")),
            get(named("users")),
            get(named("terms")),
            get(named("privacy")),
            get(named("payments")),
            get(named("subscriptions")),
            get(named("refunds")),
            get(named("serviceCatalog")),
            get(named("mock")),
            get(named("decode")),
            get(named("pollingengine"))
        )
    }
}
