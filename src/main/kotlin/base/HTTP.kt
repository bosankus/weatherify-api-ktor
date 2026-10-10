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
import io.ktor.server.plugins.compression.excludeContentType
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
val WEATHER_RATE_LIMIT = RateLimitName("weather")
val LIVE_RATE_LIMIT = RateLimitName("live")
val PAYMENT_RATE_LIMIT = RateLimitName("payment")
val FEEDBACK_RATE_LIMIT = RateLimitName("feedback")
val API_RATE_LIMIT = RateLimitName("api")
val ADMIN_RATE_LIMIT = RateLimitName("admin")

fun Application.configureHTTP() {
    val logger = LoggerFactory.getLogger("HTTP")

    // Every limiter is keyed by the spoof-resistant client address (see ClientIp.kt). The
    // auth/manual_sync/github_webhook/razorpay_webhook/bundle_fetch limiters are also used by
    // the embedded Syncling routes.
    install(RateLimit) {
        fun limiter(name: RateLimitName, limit: Int) = register(name) {
            rateLimiter(limit = limit, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }

        limiter(AUTH_RATE_LIMIT, limit = 10)
        limiter(RateLimitName("manual_sync"), limit = 5)
        limiter(RateLimitName("github_webhook"), limit = 10)
        limiter(RateLimitName("razorpay_webhook"), limit = 30)
        limiter(RateLimitName("bundle_fetch"), limit = 60)

        // Weatherify routes. Weather and live make upstream calls per request, so they are the
        // tightest read limits; payments and feedback are write paths worth guarding hard.
        limiter(WEATHER_RATE_LIMIT, limit = 30)
        limiter(LIVE_RATE_LIMIT, limit = 20)
        limiter(PAYMENT_RATE_LIMIT, limit = 10)
        limiter(FEEDBACK_RATE_LIMIT, limit = 5)
        limiter(API_RATE_LIMIT, limit = 60)
        // The admin dashboard fires several XHRs per page load.
        limiter(ADMIN_RATE_LIMIT, limit = 120)
    }

    install(Compression) {
        // SSE connections must not be compressed — gzip/deflate buffer the entire stream,
        // preventing events from reaching the client. Handled per-response via
        // Content-Encoding: identity header on the SSE route, but this is the global guard.
        gzip {
            priority = 1.0
            excludeContentType(io.ktor.http.ContentType.parse("text/event-stream"))
        }
        deflate {
            priority = 10.0
            excludeContentType(io.ktor.http.ContentType.parse("text/event-stream"))
        }
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
