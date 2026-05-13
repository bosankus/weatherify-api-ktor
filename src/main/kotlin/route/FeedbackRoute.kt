package bose.ankush.route

import bose.ankush.route.common.RouteResult
import bose.ankush.route.common.executeRoute
import bose.ankush.route.common.getQueryParameter
import bose.ankush.route.common.getRequiredParameters
import com.androidplay.core.common.Result
import domain.service.FeedbackService
import io.ktor.http.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.AuthHelper.getAuthenticatedUserOrRespond
import util.Constants

private val feedbackLogger = LoggerFactory.getLogger("FeedbackRoute")

fun Route.feedbackRoute() {
    val feedbackService: FeedbackService by inject()

    route(Constants.Api.FEEDBACK_ENDPOINT) {
        get {
            val user = call.getAuthenticatedUserOrRespond() ?: return@get
            feedbackLogger.debug("User {} accessing feedback data", user.email)

            call.executeRoute(Constants.Messages.FEEDBACK_RETRIEVED) {
                when (val result = call.getQueryParameter(Constants.Api.PARAM_ID)) {
                    is RouteResult.Success -> {
                        val feedbackResult = feedbackService.getFeedbackById(result.data)
                        when (feedbackResult) {
                            is Result.Success -> RouteResult.success(feedbackResult.data)
                            is Result.Error -> RouteResult.error(Constants.Messages.FEEDBACK_NOT_FOUND, HttpStatusCode.NotFound)
                        }
                    }
                    is RouteResult.Error -> result
                }
            }
        }

        post {
            val user = call.getAuthenticatedUserOrRespond() ?: return@post
            feedbackLogger.debug("User {} submitting feedback", user.email)

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
                        val submitResult = feedbackService.submitFeedback(
                            deviceId = params[Constants.Api.PARAM_DEVICE_ID]!!,
                            deviceOs = params[Constants.Api.PARAM_DEVICE_OS]!!,
                            title = params[Constants.Api.PARAM_FEEDBACK_TITLE]!!,
                            description = params[Constants.Api.PARAM_FEEDBACK_DESCRIPTION]!!
                        )
                        when (submitResult) {
                            is Result.Success -> RouteResult.success(submitResult.data)
                            is Result.Error -> RouteResult.error(Constants.Messages.FAILED_SAVE_FEEDBACK, HttpStatusCode.InternalServerError)
                        }
                    }
                    is RouteResult.Error -> requiredParams
                }
            }
        }

        delete {
            val user = call.getAuthenticatedUserOrRespond() ?: return@delete
            feedbackLogger.debug("User {} deleting feedback", user.email)

            call.executeRoute(Constants.Messages.FEEDBACK_REMOVED) {
                when (val result = call.getQueryParameter(Constants.Api.PARAM_ID)) {
                    is RouteResult.Success -> {
                        val deleteResult = feedbackService.deleteFeedback(result.data)
                        when (deleteResult) {
                            is Result.Success -> RouteResult.success(Unit)
                            is Result.Error -> RouteResult.error(Constants.Messages.FEEDBACK_REMOVAL_FAILED, HttpStatusCode.NotFound)
                        }
                    }
                    is RouteResult.Error -> result
                }
            }
        }
    }
}
