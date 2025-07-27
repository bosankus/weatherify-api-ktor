package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory.createOrUpdateFeedback
import bose.ankush.data.db.DatabaseFactory.deleteFeedbackById
import bose.ankush.data.db.DatabaseFactory.getFeedbackById
import bose.ankush.data.model.Feedback
import bose.ankush.route.common.RouteResult
import bose.ankush.route.common.executeRoute
import bose.ankush.route.common.getQueryParameter
import bose.ankush.route.common.getRequiredParameters
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import util.Constants

/**
 * Routes for feedback functionality
 */
fun Route.feedbackRoute() {
    authenticate("jwt-auth") {
        route(Constants.Api.FEEDBACK_ENDPOINT) {
            // Get feedback by ID
            get {
                // Access the JWT principal to get user information
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim("email")?.asString()
                println("User with email $email is accessing feedback data")

                call.executeRoute(Constants.Messages.FEEDBACK_RETRIEVED) {
                    val result = call.getQueryParameter(Constants.Api.PARAM_ID)

                    when (result) {
                        is RouteResult.Success -> {
                            val feedbackId = result.data
                            val feedback = getFeedbackById(feedbackId)
                                ?: return@executeRoute RouteResult.error(
                                    Constants.Messages.FEEDBACK_NOT_FOUND,
                                    HttpStatusCode.NotFound
                                )

                            RouteResult.success(feedback)
                        }

                        is RouteResult.Error -> result
                    }
                }
            }

            // Add new feedback
            post {
                // Access the JWT principal to get user information
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim("email")?.asString()
                println("User with email $email is submitting feedback")

                call.executeRoute(Constants.Messages.FEEDBACK_SUBMITTED) {
                    val requiredParams = call.getRequiredParameters(
                        Constants.Api.PARAM_DEVICE_ID,
                        Constants.Api.PARAM_DEVICE_OS,
                        Constants.Api.PARAM_FEEDBACK_TITLE,
                        Constants.Api.PARAM_FEEDBACK_DESCRIPTION
                    )

                    when (requiredParams) {
                        is RouteResult.Success -> {
                            val params = requiredParams.data
                            val feedback = Feedback(
                                deviceId = params[Constants.Api.PARAM_DEVICE_ID]!!,
                                deviceOs = params[Constants.Api.PARAM_DEVICE_OS]!!,
                                feedbackTitle = params[Constants.Api.PARAM_FEEDBACK_TITLE]!!,
                                feedbackDescription = params[Constants.Api.PARAM_FEEDBACK_DESCRIPTION]!!
                            )
                            // Return the feedback ID immediately and process database operation asynchronously
                            // This improves performance by not blocking the request thread
                            withContext(Dispatchers.IO) {
                                val success = createOrUpdateFeedback(feedback)
                                if (!success) {
                                    println("${Constants.Messages.FAILED_SAVE_FEEDBACK}: ${feedback.id}")
                                }
                            }

                            RouteResult.success(feedback.id)
                        }

                        is RouteResult.Error -> requiredParams
                    }
                }
            }

            // Delete feedback
            delete {
                // Access the JWT principal to get user information
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim("email")?.asString()
                println("User with email $email is deleting feedback")

                call.executeRoute(Constants.Messages.FEEDBACK_REMOVED) {
                    val result = call.getQueryParameter(Constants.Api.PARAM_ID)

                    when (result) {
                        is RouteResult.Success -> {
                            val feedbackId = result.data
                            val deleted = deleteFeedbackById(feedbackId)

                            if (deleted) {
                                RouteResult.success(Unit)
                            } else {
                                RouteResult.error(
                                    Constants.Messages.FEEDBACK_REMOVAL_FAILED,
                                    HttpStatusCode.NotFound
                                )
                            }
                        }

                        is RouteResult.Error -> result
                    }
                }
            }
        }
    }
}
