package bose.ankush.base

import com.androidplay.core.serialization.FlexibleObjectIdSerializer
import bose.ankush.data.model.UnitSerializer
import org.bson.types.ObjectId
import bose.ankush.route.common.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import kotlin.time.Duration.Companion.minutes
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.slf4j.LoggerFactory

val AUTH_RATE_LIMIT = RateLimitName("auth")

fun Application.configureHTTP() {
    val logger = LoggerFactory.getLogger("HTTP")

    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                    ?: call.request.local.remoteHost
            }
        }
        register(RateLimitName("manual_sync")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                    ?: call.request.local.remoteHost
            }
        }
        register(RateLimitName("github_webhook")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                    ?: call.request.local.remoteHost
            }
        }
        register(RateLimitName("razorpay_webhook")) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                    ?: call.request.local.remoteHost
            }
        }
        register(RateLimitName("bundle_fetch")) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                    ?: call.request.local.remoteHost
            }
        }
    }

    install(Compression) {
        gzip { priority = 1.0 }
        deflate { priority = 10.0 }
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    }

    val module = SerializersModule {
        contextual(Unit::class, UnitSerializer)
        contextual(ObjectId::class, FlexibleObjectIdSerializer)
    }

    val isDevelopment = environment.config.propertyOrNull("ktor.development")?.getString()?.toBoolean()
        ?: System.getProperty("io.ktor.development")?.toBoolean()
        ?: false

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = isDevelopment
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            allowSpecialFloatingPointValues = true
            useArrayPolymorphism = false
            encodeDefaults = false
            serializersModule = module
        })
    }

    install(StatusPages) {
        exception<io.ktor.server.plugins.CannotTransformContentToTypeException> { call, cause ->
            logger.warn("Cannot transform request content: ${cause.message}", cause)
            call.respondError(
                message = "Invalid request body. Expected JSON with Content-Type: application/json",
                data = mapOf(
                    "error" to "Content transformation failed",
                    "contentType" to call.request.contentType().toString()
                ),
                status = HttpStatusCode.BadRequest
            )
        }
        exception<Throwable> { call, cause ->
            logger.error(
                "Unhandled exception on ${call.request.httpMethod.value} ${call.request.uri}",
                cause
            )
            call.respondText(
                text = """{"status":false,"message":"Internal server error","data":null}""",
                contentType = io.ktor.http.ContentType.Application.Json,
                status = HttpStatusCode.InternalServerError
            )
        }
    }
}
