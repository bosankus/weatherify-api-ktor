package bose.ankush.base

import bose.ankush.data.model.UnitSerializer
import bose.ankush.route.common.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.slf4j.LoggerFactory

fun Application.configureHTTP() {
    val logger = LoggerFactory.getLogger("HTTP")

    // Note: Compression plugin will be added once dependency is resolved
    // For now, focusing on other performance optimizations

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
        // Content-Type is automatically set by Ktor based on the response type
        header("X-Content-Type-Options", "nosniff") // Prevent MIME type sniffing
        header("X-Frame-Options", "DENY") // Prevent clickjacking
        header("X-XSS-Protection", "1; mode=block") // Enable XSS protection
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains") // Enforce HTTPS
    }

    // Create a serializers module with the UnitSerializer
    val module = SerializersModule {
        contextual(Unit::class, UnitSerializer)
    }

    // Install ContentNegotiation with custom JSON configuration
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            allowSpecialFloatingPointValues = true
            useArrayPolymorphism = false
            serializersModule = module
        })
    }

    // Install StatusPages to handle content transformation errors globally
    install(StatusPages) {
        exception<io.ktor.server.plugins.CannotTransformContentToTypeException> { call, cause ->
            logger.warn("Cannot transform request content: ${cause.message}", cause)
            call.respondError(
                message = "Invalid request body. Expected JSON with Content-Type: application/json",
                data = mapOf(
                    "error" to "Content transformation failed",
                    "details" to (cause.message ?: "Unknown error"),
                    "contentType" to call.request.contentType().toString()
                ),
                status = HttpStatusCode.BadRequest
            )
        }
    }
}
