package bose.ankush.base

import bose.ankush.data.model.UnitSerializer
import bose.ankush.route.common.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.slf4j.LoggerFactory

fun Application.configureHTTP() {
    val logger = LoggerFactory.getLogger("HTTP")

    // Enable HTTP compression for better performance
    // This can reduce response size by 60-80% for JSON/text responses
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
        }
        // Compress JSON, text, JavaScript, and CSS by default
        // Ktor automatically compresses these content types
    }

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

    // Check if we're in development mode
    val isDevelopment = environment.config.propertyOrNull("ktor.development")?.getString()?.toBoolean()
        ?: System.getProperty("io.ktor.development")?.toBoolean()
        ?: false

    // Install ContentNegotiation with custom JSON configuration
    // Disable pretty printing in production for better performance (20-30% faster serialization)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = isDevelopment // Only pretty print in development
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            allowSpecialFloatingPointValues = true
            useArrayPolymorphism = false
            encodeDefaults = false // Don't encode default values to reduce payload size
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
