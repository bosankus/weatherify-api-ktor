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

        // Log the response for debugging
        System.err.println("Sending success response: status=$status, message=$message")
        
        respond(
            status = status,
            message = response
        )
    } catch (e: Exception) {
        // Log the error with detailed information
        System.err.println("Error creating success response: ${e.message}")
        System.err.println("Response data type: ${T::class.java.simpleName}")
        System.err.println("Response message: $message")
        System.err.println("Exception type: ${e.javaClass.name}")
        System.err.println("Stack trace: ${e.stackTraceToString()}")

        try {
            // Try to respond with a simpler response
            System.err.println("Attempting to send simplified success response")
            respond(
                status = status, // Keep the original status code
                message = ApiResponse(
                    status = true,
                    message = message,
                    data = null
                )
            )
        } catch (e2: Exception) {
            // If that fails too, use the most basic response possible
            System.err.println("Failed to send simplified success response: ${e2.message}")
            System.err.println("Exception type: ${e2.javaClass.name}")
            System.err.println("Stack trace: ${e2.stackTraceToString()}")

            // Use the most basic response format
            System.err.println("Attempting to send basic map response")
            respond(
                status = status,
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

        // Log the response for debugging
        System.err.println("Sending error response: status=$status, message=$message")

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
        System.err.println("Exception type: ${e.javaClass.name}")
        System.err.println("Stack trace: ${e.stackTraceToString()}")

        try {
            // Try to respond with a simpler response, but keep the original status code
            System.err.println("Attempting to send simplified error response")
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
            System.err.println("Exception type: ${e2.javaClass.name}")
            System.err.println("Stack trace: ${e2.stackTraceToString()}")

            // Use the most basic response format
            System.err.println("Attempting to send basic map response")
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
