package bose.ankush.route.common

import bose.ankush.data.model.ApiResponse
import bose.ankush.data.model.FlexibleObjectIdSerializer
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
/**
 * Set cache control headers for responses.
 * @param maxAgeSeconds Maximum age in seconds for the cache (default: 300 = 5 minutes)
 * @param isPublic Whether the response can be cached by public caches (default: true)
 * @param mustRevalidate Whether the cache must revalidate before serving stale content (default: true)
 */
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

/** Responds with a success message */
suspend inline fun <reified T> ApplicationCall.respondSuccess(
    message: String,
    data: T,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    // Ensure data is null for Unit responses while preserving generic type
    val actualData: T? = if (data is Unit) null else data

    val response: ApiResponse<T?> = ApiResponse(status = true, message = message, data = actualData)

    // Build a Json instance consistent with HTTP configuration with ObjectId serializer
    val json = Json {
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

    // Obtain serializers for the generic payload and the ApiResponse
    val payloadSerializer: KSerializer<T?> = serializer()
    val apiResponseSerializer = ApiResponse.serializer(payloadSerializer)

    val body = json.encodeToString(apiResponseSerializer, response)

    respondText(text = body, contentType = ContentType.Application.Json, status = status)
}

/**
 * Create a Json instance with proper serializers module for ObjectId and other types
 * Use this function whenever you need to serialize/deserialize data models with ObjectId
 */
fun createJsonWithSerializers(ignoreUnknownKeys: Boolean = true): Json = Json {
    prettyPrint = true
    encodeDefaults = true
    isLenient = true
    this.ignoreUnknownKeys = ignoreUnknownKeys
    coerceInputValues = true
    allowSpecialFloatingPointValues = true
    useArrayPolymorphism = false
    serializersModule = SerializersModule {
        contextual(ObjectId::class, FlexibleObjectIdSerializer)
    }
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
        serializersModule = SerializersModule {
            contextual(ObjectId::class, FlexibleObjectIdSerializer)
        }
    }

    val payloadSerializer: KSerializer<T?> = serializer()
    val apiResponseSerializer = ApiResponse.serializer(payloadSerializer)

    val body = json.encodeToString(apiResponseSerializer, response)

    respondText(text = body, contentType = ContentType.Application.Json, status = status)
}
