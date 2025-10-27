package bose.ankush.route.common

import bose.ankush.data.model.ApiResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** Responds with a success message */
suspend inline fun <reified T> ApplicationCall.respondSuccess(
    message: String,
    data: T,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    // Ensure data is null for Unit responses while preserving generic type
    val actualData: T? = if (data is Unit) null else data

    val response: ApiResponse<T?> = ApiResponse(status = true, message = message, data = actualData)

    // Build a Json instance consistent with HTTP configuration
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = false
    }

    // Obtain serializers for the generic payload and the ApiResponse
    val payloadSerializer: KSerializer<T?> = serializer()
    val apiResponseSerializer = ApiResponse.serializer(payloadSerializer)

    val body = json.encodeToString(apiResponseSerializer, response)

    // Log the response for debugging
    System.err.println("Sending success response: status=$status, message=$message")

    respondText(text = body, contentType = ContentType.Application.Json, status = status)
}

/** Responds with an error message */
suspend inline fun <reified T> ApplicationCall.respondError(
    message: String,
    data: T,
    status: HttpStatusCode = HttpStatusCode.BadRequest
) {
    // Ensure data is null for Unit responses while preserving generic type
    val actualData: T? = if (data is Unit) null else data

    val response: ApiResponse<T?> =
        ApiResponse(status = false, message = message, data = actualData)

    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = false
    }

    val payloadSerializer: KSerializer<T?> = serializer()
    val apiResponseSerializer = ApiResponse.serializer(payloadSerializer)

    val body = json.encodeToString(apiResponseSerializer, response)

    // Log the response for debugging
    System.err.println("Sending error response: status=$status, message=$message")

    respondText(text = body, contentType = ContentType.Application.Json, status = status)
}
