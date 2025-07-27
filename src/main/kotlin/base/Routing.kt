package bose.ankush.base

import bose.ankush.route.authRoute
import bose.ankush.route.feedbackRoute
import bose.ankush.route.homeRoute
import bose.ankush.route.privacyPolicyRoute
import bose.ankush.route.termsAndConditionsRoute
import bose.ankush.route.weatherRoute
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        homeRoute()
        weatherRoute()
        feedbackRoute()
        authRoute()
        termsAndConditionsRoute()
        privacyPolicyRoute()
    }
}
