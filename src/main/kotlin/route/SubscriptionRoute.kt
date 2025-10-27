package bose.ankush.route

import bose.ankush.data.model.SubscriptionHistoryResponse
import bose.ankush.data.model.SubscriptionResponse
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import domain.model.Result
import domain.service.SubscriptionService
import io.ktor.http.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.AuthHelper.getAuthenticatedUserOrRespond
import util.Constants

/**
 * User-facing subscription routes
 * - Get current subscription status
 * - Get subscription history
 * - Cancel active subscription
 */
fun Route.subscriptionRoute() {
    val subscriptionService: SubscriptionService by application.inject()
    val logger = LoggerFactory.getLogger("SubscriptionRoute")

    route(Constants.Subscription.SUBSCRIPTIONS_BASE) {
        // GET /subscriptions/status - Get current subscription status
        get("/status") {
            val user = call.getAuthenticatedUserOrRespond() ?: return@get
            logger.debug("Getting subscription status for user: ${user.email}")

            when (val result = subscriptionService.getSubscriptionStatus(user.email)) {
                is Result.Success -> {
                    call.respondSuccess<SubscriptionResponse>(
                        "Subscription status retrieved",
                        result.data
                    )
                }

                is Result.Error -> {
                    val statusCode = if (result.message.contains("not found", ignoreCase = true)) {
                        HttpStatusCode.NotFound
                    } else {
                        HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // GET /subscriptions/history - Get subscription history
        get("/history") {
            val user = call.getAuthenticatedUserOrRespond() ?: return@get
            logger.debug("Getting subscription history for user: ${user.email}")

            when (val result = subscriptionService.getSubscriptionHistory(user.email)) {
                is Result.Success -> {
                    call.respondSuccess<SubscriptionHistoryResponse>(
                        "Subscription history retrieved",
                        result.data
                    )
                }

                is Result.Error -> {
                    val statusCode = if (result.message.contains("not found", ignoreCase = true)) {
                        HttpStatusCode.NotFound
                    } else {
                        HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // POST /subscriptions/cancel - Cancel active subscription
        post("/cancel") {
            val user = call.getAuthenticatedUserOrRespond() ?: return@post
            logger.debug("Cancelling subscription for user: ${user.email}")

            when (val result = subscriptionService.cancelSubscription(user.email)) {
                is Result.Success -> {
                    call.respondSuccess<SubscriptionResponse>(
                        "Subscription cancelled successfully",
                        result.data
                    )
                }

                is Result.Error -> {
                    val statusCode = when {
                        result.message.contains("not found", ignoreCase = true) -> HttpStatusCode.NotFound
                        result.message.contains(
                            "no active subscription",
                            ignoreCase = true
                        ) -> HttpStatusCode.BadRequest

                        result.message.contains("already cancelled", ignoreCase = true) -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }
    }
}
