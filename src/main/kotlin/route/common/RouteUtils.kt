package bose.ankush.route.common

import bose.ankush.data.model.FeedbackResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/**
 * Responds with a success message
 */
suspend inline fun <reified T> ApplicationCall.respondSuccess(
    message: String,
    data: T,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respond(
        status = status,
        message = FeedbackResponse(
            status = true,
            message = message,
            data = data
        )
    )
}

/**
 * Responds with an error message
 */
suspend inline fun <reified T> ApplicationCall.respondError(
    message: String,
    data: T,
    status: HttpStatusCode = HttpStatusCode.BadRequest
) {
    respond(
        status = status,
        message = FeedbackResponse(
            status = false,
            message = message,
            data = data
        )
    )
}
