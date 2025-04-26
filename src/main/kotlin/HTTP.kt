package bose.ankush

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
        header("Content-Type", "application/json") // Accept JSON
        header("X-Content-Type-Options", "nosniff") // Prevent MIME type sniffing
        header("X-Frame-Options", "DENY") // Prevent clickjacking
        header("X-XSS-Protection", "1; mode=block") // Enable XSS protection
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains") // Enforce HTTPS
    }
}
