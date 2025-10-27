package bose.ankush.base

import bose.ankush.data.model.UnitSerializer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

fun Application.configureHTTP() {
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
}
