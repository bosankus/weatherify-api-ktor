package bose.ankush.route.common

import io.ktor.http.*
import io.ktor.server.application.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Result type for handling route operations
 */
sealed class RouteResult<out T> {
    data class Success<T>(val data: T) : RouteResult<T>()
    data class Error(val message: String, val statusCode: HttpStatusCode = HttpStatusCode.BadRequest) :
        RouteResult<Nothing>()

    companion object {
        fun <T> success(data: T): RouteResult<T> = Success(data)
        fun error(message: String, statusCode: HttpStatusCode = HttpStatusCode.BadRequest): RouteResult<Nothing> =
            Error(message, statusCode)
    }
}

/**
 * Extension function to extract a query parameter safely
 * @param name Name of the parameter to extract
 * @return RouteResult with the parameter value or error
 */
fun ApplicationCall.getQueryParameter(name: String): RouteResult<String> {
    val value = request.queryParameters[name]
    return if (value.isNullOrBlank()) {
        RouteResult.error("Missing or empty parameter: $name")
    } else {
        RouteResult.success(value)
    }
}

/**
 * Extension function to extract multiple required query parameters
 * @param names Names of required parameters
 * @return RouteResult with a map of parameter names to values, or error if any are missing
 */
fun ApplicationCall.getRequiredParameters(vararg names: String): RouteResult<Map<String, String>> {
    val params = mutableMapOf<String, String>()

    for (name in names) {
        when (val result = getQueryParameter(name)) {
            is RouteResult.Success -> params[name] = result.data
            is RouteResult.Error -> return result
        }
    }

    return RouteResult.success(params)
}

/**
 * Handle the result of a route operation and respond accordingly
 * @param result RouteResult to process
 * @param successMessage Message to send on success
 */
suspend inline fun <reified T> ApplicationCall.handleRouteResult(
    result: RouteResult<T>, successMessage: String
) {
    when (result) {
        is RouteResult.Success -> respondSuccess(successMessage, result.data)
        is RouteResult.Error -> respondError(result.message, Unit, result.statusCode)
    }
}

/**
 * Execute a block that returns a RouteResult and handle the response
 * @param successMessage Message to send on success
 * @param block Block that returns a RouteResult
 */
@OptIn(ExperimentalContracts::class)
suspend inline fun <reified T> ApplicationCall.executeRoute(
    successMessage: String,
    block: () -> RouteResult<T>
) {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val result = try {
        block()
    } catch (e: Exception) {
        RouteResult.error(e.message ?: "Unknown error", HttpStatusCode.InternalServerError)
    }
    handleRouteResult(result, successMessage)
}