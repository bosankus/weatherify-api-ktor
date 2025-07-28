package bose.ankush.route.common

import bose.ankush.data.model.ApiResponse
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
    try {
        // For Unit type, use null as data to avoid serialization issues
        val actualData = if (data is Unit) null else data

        val response = ApiResponse(
            status = true,
            message = message,
            data = actualData
        )

        respond(
            status = status,
            message = response
        )
    } catch (e: Exception) {
        // Log the error with detailed information
        System.err.println("Error creating success response: ${e.message}")
        System.err.println("Response data type: ${T::class.java.simpleName}")
        System.err.println("Response message: $message")
        e.printStackTrace()

        try {
            // Try to respond with a simpler response
            respond(
                status = HttpStatusCode.OK, // Keep the original status code
                message = ApiResponse(
                    status = true,
                    message = message,
                    data = null
                )
            )
        } catch (e2: Exception) {
            // If that fails too, use the most basic response possible
            System.err.println("Failed to send simplified success response: ${e2.message}")
            e2.printStackTrace()

            respond(
                status = HttpStatusCode.OK,
                message = mapOf(
                    "status" to true,
                    "message" to message
                )
            )
        }
    }
}

/**
 * Responds with an error message
 */
suspend inline fun <reified T> ApplicationCall.respondError(
    message: String,
    data: T,
    status: HttpStatusCode = HttpStatusCode.BadRequest
) {
    try {
        // For Unit type, use null as data to avoid serialization issues
        val actualData = if (data is Unit) null else data

        val response = ApiResponse(
            status = false,
            message = message,
            data = actualData
        )

        respond(
            status = status,
            message = response
        )
    } catch (e: Exception) {
        // Log the error with detailed information
        System.err.println("Error creating error response: ${e.message}")
        System.err.println("Response data type: ${T::class.java.simpleName}")
        System.err.println("Response message: $message")
        System.err.println("Response status code: $status")
        e.printStackTrace()

        try {
            // Try to respond with a simpler response, but keep the original status code
            respond(
                status = status, // Keep the original status code
                message = ApiResponse(
                    status = false,
                    message = message,
                    data = null
                )
            )
        } catch (e2: Exception) {
            // If that fails too, use the most basic response possible
            System.err.println("Failed to send simplified error response: ${e2.message}")
            e2.printStackTrace()

            respond(
                status = status, // Keep the original status code
                message = mapOf(
                    "status" to false,
                    "message" to message
                )
            )
        }
    }
}
