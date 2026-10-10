package bose.ankush.route.common

import bose.ankush.base.*
import bose.ankush.route.*
import domain.service.SavedLocationService
import domain.service.PlaceEventService
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.get

object WeatherRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(WEATHER_RATE_LIMIT) { weatherRoute() }
    }
}

object FeedbackRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(FEEDBACK_RATE_LIMIT) { feedbackRoute() }
    }
}

object AuthRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        with(root) { authRoute() }
    }
}

object AdminAuthRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(ADMIN_RATE_LIMIT) { adminAuthRoute() }
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
        root.rateLimit(ADMIN_RATE_LIMIT) { userRoute() }
        root.rateLimit(API_RATE_LIMIT) { accountPhotoRoute() }
    }
}

object PaymentRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(PAYMENT_RATE_LIMIT) { paymentRoute() }
    }
}

object MockRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(API_RATE_LIMIT) { mockApiRoute() }
    }
}

object DecodeRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(API_RATE_LIMIT) { decodeRoute() }
    }
}

object PollingEngineRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(API_RATE_LIMIT) { pollingEngineRoute() }
    }
}

object RefundRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(PAYMENT_RATE_LIMIT) { refundRoute() }
    }
}

object ServiceCatalogRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(API_RATE_LIMIT) { serviceCatalogRoute() }
    }
}

object SavedLocationRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(API_RATE_LIMIT) { savedLocationRoute(get(SavedLocationService::class.java)) }
    }
}

object NoteRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(API_RATE_LIMIT) { noteRoute() }
    }
}

object LiveWeatherRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(LIVE_RATE_LIMIT) { liveWeatherRoute() }
    }
}

object PlaceEventRoutesRegistrar : RouteRegistrar {
    override fun register(root: Route) {
        root.rateLimit(API_RATE_LIMIT) { placeEventRoute(get(PlaceEventService::class.java)) }
    }
}
