package di

import bose.ankush.route.common.AdminAuthRoutesRegistrar
import bose.ankush.route.common.AuthRoutesRegistrar
import bose.ankush.route.common.FeedbackRoutesRegistrar
import bose.ankush.route.common.HomeRoutesRegistrar
import bose.ankush.route.common.PrivacyPolicyRoutesRegistrar
import bose.ankush.route.common.RouteRegistrar
import bose.ankush.route.common.TermsAndConditionsRoutesRegistrar
import bose.ankush.route.common.WeatherRoutesRegistrar
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
    single<RouteRegistrar>(named("terms")) { TermsAndConditionsRoutesRegistrar }
    single<RouteRegistrar>(named("privacy")) { PrivacyPolicyRoutesRegistrar }

    // Provide ordered list
    single<List<RouteRegistrar>> {
        listOf(
            get(named("home")),
            get(named("weather")),
            get(named("feedback")),
            get(named("auth")),
            get(named("adminAuth")),
            get(named("terms")),
            get(named("privacy"))
        )
    }
}
