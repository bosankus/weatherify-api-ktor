package bose.ankush.route.common

import bose.ankush.route.*
import io.ktor.server.routing.*
import route.savedLocationRoute

/**
 * Registrar implementations that simply delegate to existing route extension functions.
 * This keeps behavior unchanged while enabling modular registration.
 */
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

object UserRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { userRoute() }
    }
}

object PaymentRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { paymentRoute() }
    }
}

object MockRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { mockApiRoute() }
    }
}

object DecodeRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { decodeRoute() }
    }
}

object PollingEngineRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { pollingEngineRoute() }
    }
}

object RefundRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { refundRoute() }
    }
}

object ServiceCatalogRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { serviceCatalogRoute() }
    }
}

object SavedLocationRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { savedLocationRoute(org.koin.java.KoinJavaComponent.get(domain.service.SavedLocationService::class.java)) }
    }
}
