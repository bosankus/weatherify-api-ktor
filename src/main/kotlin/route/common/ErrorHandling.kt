package bose.ankush.route.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Result type for route operations */
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

/** Extract a query parameter safely */
fun ApplicationCall.getQueryParameter(name: String): RouteResult<String> {
    val value = request.queryParameters[name]
    return if (value.isNullOrBlank()) {
        RouteResult.error("Missing or empty parameter: $name")
    } else {
        RouteResult.success(value)
    }
}

/** Extract multiple required query parameters */
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

/** Handle the result of a route operation and respond accordingly */
suspend inline fun <reified T> ApplicationCall.handleRouteResult(
    result: RouteResult<T>, successMessage: String
) {
    when (result) {
        is RouteResult.Success -> respondSuccess(successMessage, result.data)
        is RouteResult.Error -> respondError(result.message, Unit, result.statusCode)
    }
}

/** Execute a block that returns a RouteResult and handle the response */
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