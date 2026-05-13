package bose.ankush.route.common

import bose.ankush.data.model.ApiResponse
import com.androidplay.core.serialization.FlexibleObjectIdSerializer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.serializer
import org.bson.types.ObjectId

fun ApplicationCall.setCacheHeaders(
    maxAgeSeconds: Int = 300,
    isPublic: Boolean = true,
    mustRevalidate: Boolean = true
) {
    val cacheControl = buildString {
        if (isPublic) append("public, ") else append("private, ")
        append("max-age=$maxAgeSeconds")
        if (mustRevalidate) append(", must-revalidate")
    }
    response.headers.append("Cache-Control", cacheControl)
    response.headers.append("Vary", "Accept-Encoding")
}

val sharedJson: Json = Json {
    prettyPrint = true
    encodeDefaults = true
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    allowSpecialFloatingPointValues = true
    useArrayPolymorphism = false
    serializersModule = SerializersModule {
        contextual(ObjectId::class, FlexibleObjectIdSerializer)
    }
}

suspend inline fun <reified T> ApplicationCall.respondSuccess(
    message: String,
    data: T,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    val actualData: T? = if (data is Unit) null else data
    val response: ApiResponse<T?> = ApiResponse(status = true, message = message, data = actualData)
    val payloadSerializer: KSerializer<T?> = serializer()
    val apiResponseSerializer = ApiResponse.serializer(payloadSerializer)

    val body = sharedJson.encodeToString(apiResponseSerializer, response)

    respondText(text = body, contentType = ContentType.Application.Json, status = status)
}

suspend inline fun <reified T> ApplicationCall.respondError(
    message: String,
    data: T,
    status: HttpStatusCode = HttpStatusCode.BadRequest
) {
    val actualData: T? = if (data is Unit) null else data
    val response: ApiResponse<T?> = ApiResponse(status = false, message = message, data = actualData)

    val payloadSerializer: KSerializer<T?> = serializer()
    val apiResponseSerializer = ApiResponse.serializer(payloadSerializer)

    val body = sharedJson.encodeToString(apiResponseSerializer, response)

    respondText(text = body, contentType = ContentType.Application.Json, status = status)
}
