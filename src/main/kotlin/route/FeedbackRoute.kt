package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory.createOrUpdateFeedback
import bose.ankush.data.db.DatabaseFactory.deleteFeedbackById
import bose.ankush.data.db.DatabaseFactory.getFeedbackById
import bose.ankush.data.model.Feedback
import bose.ankush.route.common.*
import io.ktor.http.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*

/**
 * Routes for feedback functionality
 */
fun Route.feedbackRoute() {
    install(ContentNegotiation) {
        defaultJson()
    }

    route("/feedback") {
        // Get feedback by ID
        get {
            call.executeRoute("Feedback retrieved successfully") {
                val result = call.getQueryParameter("id")

                when (result) {
                    is RouteResult.Success -> {
                        val feedbackId = result.data
                        val feedback = getFeedbackById(feedbackId)
                            ?: return@executeRoute RouteResult.error("Feedback not found", HttpStatusCode.NotFound)

                        RouteResult.success(feedback)
                    }

                    is RouteResult.Error -> result
                }
            }
        }

        // Add new feedback
        post {
            call.executeRoute("Feedback submitted successfully") {
                val requiredParams = call.getRequiredParameters(
                    "deviceId", "deviceOs", "feedbackTitle", "feedbackDescription"
                )

                when (requiredParams) {
                    is RouteResult.Success -> {
                        val params = requiredParams.data
                        val feedback = Feedback(
                            deviceId = params["deviceId"]!!,
                            deviceOs = params["deviceOs"]!!,
                            feedbackTitle = params["feedbackTitle"]!!,
                            feedbackDescription = params["feedbackDescription"]!!
                        )

                        val success = createOrUpdateFeedback(feedback)
                        if (success) {
                            RouteResult.success(feedback.id)
                        } else {
                            RouteResult.error("Failed to save feedback", HttpStatusCode.Conflict)
                        }
                    }

                    is RouteResult.Error -> requiredParams
                }
            }
        }

        // Delete feedback
        delete {
            call.executeRoute("Feedback removed successfully") {
                val result = call.getQueryParameter("id")

                when (result) {
                    is RouteResult.Success -> {
                        val feedbackId = result.data
                        val deleted = deleteFeedbackById(feedbackId)

                        if (deleted) {
                            RouteResult.success(Unit)
                        } else {
                            RouteResult.error("Feedback not found or could not be removed", HttpStatusCode.NotFound)
                        }
                    }

                    is RouteResult.Error -> result
                }
            }
        }
    }
}
