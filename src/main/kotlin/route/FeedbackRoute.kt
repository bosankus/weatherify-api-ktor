package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory.createOrUpdateFeedback
import bose.ankush.data.db.DatabaseFactory.deleteFeedbackById
import bose.ankush.data.db.DatabaseFactory.getFeedbackById
import bose.ankush.data.model.Feedback
import bose.ankush.data.model.FeedbackResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.feedbackRoute() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            allowSpecialFloatingPointValues = true
            useArrayPolymorphism = false
        })
    }
    route("/get-feedback") {
        get {
            val feedbackId = call.request.queryParameters["id"]
            val feedback = feedbackId?.let { id -> getFeedbackById(id = id) }
                ?: call.respond(
                    status = HttpStatusCode.OK,
                    message = FeedbackResponse(
                        status = false,
                        message = "Incorrect or no data provided",
                        data = Unit
                    )
                )

            call.respond(
                status = HttpStatusCode.OK,
                message = FeedbackResponse(
                    status = true,
                    message = "Feedback retrieved successfully",
                    data = feedback
                )
            )
        }
    }

    route("/add-feedback") {
        post {
            val request: Feedback = try {
                val parameters = call.request.queryParameters
                Feedback(
                    deviceId = parameters["deviceId"].toString(),
                    deviceOs = parameters["deviceOs"].toString(),
                    feedbackTitle = parameters["feedbackTitle"].toString(),
                    feedbackDescription = parameters["feedbackDescription"].toString()
                )
            } catch (e: Exception) {
                e.message
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = FeedbackResponse(
                        status = false,
                        message = HttpStatusCode.BadRequest.description,
                        data = Unit
                    )
                )
                return@post
            }

            if (createOrUpdateFeedback(request)) {
                call.respond(

                    status = HttpStatusCode.OK,
                    message = FeedbackResponse(
                        status = true,
                        message = "Feedback submitted successfully",
                        data = request.id
                    )
                )
            } else {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = FeedbackResponse(
                        status = true,
                        message = HttpStatusCode.Conflict.description,
                        data = Unit
                    )
                )
            }
        }
    }

    route("/remove-feedback") {
        delete {
            val feedbackId = call.request.queryParameters["id"]
            val isFeedbackDeleted = feedbackId?.let { id -> deleteFeedbackById(id) } ?: false

            if (isFeedbackDeleted) {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = FeedbackResponse(
                        status = true,
                        message = "Feedback removed successfully",
                        data = Unit
                    )
                )
            } else {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = FeedbackResponse(
                        status = true,
                        message = "Non-existent feedback can't be removed",
                        data = Unit
                    )
                )
            }
        }
    }
}
