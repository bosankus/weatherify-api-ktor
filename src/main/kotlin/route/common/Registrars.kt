package bose.ankush.route.common

import bose.ankush.route.adminAuthRoute
import bose.ankush.route.authRoute
import bose.ankush.route.feedbackRoute
import bose.ankush.route.homeRoute
import bose.ankush.route.privacyPolicyRoute
import bose.ankush.route.termsAndConditionsRoute
import bose.ankush.route.weatherRoute
import io.ktor.server.routing.Route

/**
 * Registrar implementations that simply delegate to existing route extension functions.
 * This keeps behavior unchanged while enabling modular registration.
 */
object HomeRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { homeRoute() }
    }
}

object WeatherRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { weatherRoute() }
    }
}

object FeedbackRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { feedbackRoute() }
    }
}

object AuthRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { authRoute() }
    }
}

object AdminAuthRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { adminAuthRoute() }
    }
}

object TermsAndConditionsRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { termsAndConditionsRoute() }
    }
}

object PrivacyPolicyRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { privacyPolicyRoute() }
    }
}
