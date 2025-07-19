package bose.ankush.route.common

import bose.ankush.data.model.FeedbackResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

/**
 * Default JSON configuration for ContentNegotiation
 */
fun ContentNegotiationConfig.defaultJson() {
    json(Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = false
    })
}

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
