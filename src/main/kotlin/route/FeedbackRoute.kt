package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory.createOrUpdateFeedback
import bose.ankush.data.db.DatabaseFactory.deleteFeedbackById
import bose.ankush.data.db.DatabaseFactory.getFeedbackById
import bose.ankush.data.model.Feedback
import bose.ankush.route.common.RouteResult
import bose.ankush.route.common.executeRoute
import bose.ankush.route.common.getQueryParameter
import bose.ankush.route.common.getRequiredParameters
import io.ktor.http.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import util.AuthHelper.getAuthenticatedUserOrRespond
import util.Constants

/**
 * Routes for feedback functionality
 */
fun Route.feedbackRoute() {
    route(Constants.Api.FEEDBACK_ENDPOINT) {
        // Get feedback by ID
        get {
            // Authenticate user using unified helper
            val user = call.getAuthenticatedUserOrRespond() ?: return@get
            println("User with email ${user.email} is accessing feedback data")

            call.executeRoute(Constants.Messages.FEEDBACK_RETRIEVED) {
                when (val result = call.getQueryParameter(Constants.Api.PARAM_ID)) {
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
            // Authenticate user using unified helper
            val user = call.getAuthenticatedUserOrRespond() ?: return@post
            println("User with email ${user.email} is submitting feedback")

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
            // Authenticate user using unified helper
            val user = call.getAuthenticatedUserOrRespond() ?: return@delete
            println("User with email ${user.email} is deleting feedback")

            call.executeRoute(Constants.Messages.FEEDBACK_REMOVED) {
                when (val result = call.getQueryParameter(Constants.Api.PARAM_ID)) {
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
