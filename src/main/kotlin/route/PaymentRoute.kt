package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory
import bose.ankush.data.model.*
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import bose.ankush.util.SignatureUtil
import bose.ankush.util.getSecretValue
import config.JwtConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import util.Constants
import java.util.*

private val paymentLogger = LoggerFactory.getLogger("PaymentRoute")
private val razorpayClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
}

private val relaxedJson: Json = Json { ignoreUnknownKeys = true }

private suspend fun ApplicationCall.getJwtPrincipalOrRespond(): JWTPrincipal? {
    // Extract token from Authorization header or cookie
    val authHeader = request.headers["Authorization"]
    val jwtToken = if (authHeader != null && authHeader.startsWith("Bearer ")) {
        authHeader.substring(7)
    } else {
        request.cookies["jwt_token"]
    }

    if (jwtToken != null) {
        try {
            val decodedJWT = JwtConfig.verifier.verify(jwtToken)
            val email = decodedJWT.getClaim("email").asString()
            paymentLogger.info("JWT principal created for user: $email")
            return JWTPrincipal(decodedJWT)
        } catch (e: Exception) {
            paymentLogger.warn("Failed to verify JWT: ${e.message}", e)
            respondError(
                message = "Session expired or invalid. Please login again.",
                data = null,
                status = HttpStatusCode.Unauthorized
            )
            return null
        }
    }

    paymentLogger.warn("No JWT principal found for payment API access attempt")
    respondError(
        message = "Authentication required. Please login.",
        data = null,
        status = HttpStatusCode.Unauthorized
    )
    return null
}

/** Payment routes: create order and verify payment */
fun Route.paymentRoute() {
    // POST /create-order -> Create Razorpay order server-side
    post(Constants.Api.CREATE_ORDER_ENDPOINT) {
        call.getJwtPrincipalOrRespond() ?: return@post

        val keyId = getSecretValue(Constants.Auth.RAZORPAY_KEY_ID)
        val keySecret = getSecretValue(Constants.Auth.RAZORPAY_SECRET)
        if (keyId.isBlank() || keySecret.isBlank() || keyId.startsWith("dummy_") || keySecret.startsWith(
                "dummy_"
            ) || keyId.startsWith("dummy_value_for_") || keySecret.startsWith("dummy_value_for_") ||
            keyId.startsWith("fallback_value_for_") || keySecret.startsWith("fallback_value_for_")
        ) {
            call.respondError(
                message = "Payment not configured. Please contact support.",
                data = Unit,
                status = HttpStatusCode.InternalServerError
            )
            return@post
        }

        val req = try {
            call.receive<CreateOrderRequest>()
        } catch (e: Exception) {
            paymentLogger.warn("Malformed JSON payload for create order", e)
            call.respondError(
                message = "Malformed JSON payload",
                data = Unit,
                status = HttpStatusCode.BadRequest
            )
            return@post
        }

        // Basic validation
        if (req.amount <= 0 || req.currency.isBlank() || req.receipt.isBlank()) {
            call.respondError(
                message = "Missing/invalid fields: amount (>0), currency, receipt",
                data = Unit,
                status = HttpStatusCode.BadRequest
            )
            return@post
        }

        try {
            val basic = Base64.getEncoder().encodeToString("$keyId:$keySecret".toByteArray())
            val payload = RazorpayCreateOrderPayload(
                amount = req.amount,
                currency = req.currency,
                receipt = req.receipt,
                partialPayment = req.partialPayment,
                firstPaymentMinAmount = req.firstPaymentMinAmount,
                notes = req.notes
            )

            val response: HttpResponse = razorpayClient.post("https://api.razorpay.com/v1/orders") {
                headers.append(HttpHeaders.Authorization, "Basic $basic")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            val bodyText = response.bodyAsText()
            if (response.status.value in 200..299) {
                val rp = relaxedJson.decodeFromString(
                    RazorpayOrderResponse.serializer(),
                    bodyText
                )
                call.respondSuccess(
                    message = "Order created successfully",
                    data = CreateOrderResponse(
                        orderId = rp.id,
                        amount = rp.amount,
                        currency = rp.currency,
                        receipt = rp.receipt,
                        status = rp.status,
                        createdAt = rp.createdAt
                    ),
                    status = HttpStatusCode.OK
                )
            } else {
                // Try to parse Razorpay error; if fails, use raw body
                val err = try {
                    relaxedJson.decodeFromString(
                        RazorpayErrorResponse.serializer(),
                        bodyText
                    )
                } catch (_: Exception) {
                    null
                }
                val msg = err?.error?.description ?: "Failed to create order"
                call.respondError(
                    message = msg,
                    data = bodyText,
                    status = HttpStatusCode.fromValue(response.status.value)
                )
            }
        } catch (e: Exception) {
            paymentLogger.error("Failed to create Razorpay order", e)
            call.respondError(
                message = "Failed to create order",
                data = Unit,
                status = HttpStatusCode.InternalServerError
            )
        }
    }


    // POST /store-payment -> Verify and store payment details
    post(Constants.Api.STORE_PAYMENT_ENDPOINT) {
        val principal = call.getJwtPrincipalOrRespond() ?: return@post

        val secret = getSecretValue(Constants.Auth.RAZORPAY_SECRET)
        if (secret.isBlank() || secret.startsWith("dummy_") || secret.startsWith("dummy_value_for_") || secret.startsWith(
                "fallback_value_for_"
            )
        ) {
            call.respondError(
                message = "Payment verification not configured. Please contact support.",
                data = Unit,
                status = HttpStatusCode.InternalServerError
            )
            return@post
        }

        val payload = try {
            call.receive<VerifyPaymentRequest>()
        } catch (e: Exception) {
            paymentLogger.warn("Malformed JSON payload for store payment", e)
            call.respondError(
                message = "Malformed JSON payload",
                data = Unit,
                status = HttpStatusCode.BadRequest
            )
            return@post
        }

        val paymentId = payload.paymentId?.trim()
        val orderId = payload.orderId?.trim()
        val signature = payload.signature?.trim()?.lowercase()

        if (paymentId.isNullOrEmpty() || orderId.isNullOrEmpty() || signature.isNullOrEmpty()) {
            call.respondError(
                message = "Missing required fields: razorpay_payment_id, razorpay_order_id, razorpay_signature",
                data = Unit,
                status = HttpStatusCode.BadRequest
            )
            return@post
        }

        // Verify signature
        val dataString = "$orderId|$paymentId"
        val generated = try {
            SignatureUtil.secure(dataString, secret)
        } catch (e: Exception) {
            paymentLogger.error("Failed to compute signature for store payment", e)
            call.respondError(
                message = "Failed to compute signature",
                data = Unit,
                status = HttpStatusCode.InternalServerError
            )
            return@post
        }

        val matches = SignatureUtil.compare(generated, signature)
        if (!matches) {
            call.respondError(
                message = "Signature verification failed",
                data = VerifyPaymentResponse(verified = false),
                status = HttpStatusCode.BadRequest
            )
            return@post
        }

        // Extract user email from JWT principal to link with User
        val userEmail = try {
            principal.getClaim(Constants.Auth.JWT_CLAIM_EMAIL, String::class)
        } catch (_: Exception) {
            null
        } ?: run {
            paymentLogger.warn("Email claim missing from JWT; cannot link payment to user")
            call.respondError(
                message = "Authentication error: email not found in token",
                data = Unit,
                status = HttpStatusCode.Unauthorized
            )
            return@post
        }

        // Persist payment and update subscription
        // Fetch user by email to link payment and update subscriptions
        val user = try {
            DatabaseFactory.findUserByEmail(userEmail)
        } catch (e: Exception) {
            paymentLogger.error("Failed to fetch user by email for payment storing: $userEmail", e)
            null
        }

        if (user == null) {
            call.respondError(
                message = "User not found for payment processing",
                data = Unit,
                status = HttpStatusCode.NotFound
            )
            return@post
        }

        // Determine subscription window (30 days)
        val nowInstant = java.time.Instant.now()
        val startDate = nowInstant.toString()
        val endDate = nowInstant.plusSeconds(30L * 24L * 60L * 60L).toString()

        // Admin tracking context
        val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
            ?: call.request.headers["X-Real-IP"]
        val ua = call.request.headers["User-Agent"]

        val payment = Payment(
            userEmail = userEmail,
            orderId = orderId,
            paymentId = paymentId,
            signature = signature,
            status = "verified",
            verifiedAt = startDate,
            userId = user.id.toHexString(),
            serviceType = ServiceType.PREMIUM_ONE,
            subscriptionStart = startDate,
            subscriptionEnd = endDate,
            requestIp = ip,
            userAgent = ua
        )

        val saved = try {
            DatabaseFactory.savePayment(payment)
        } catch (e: Exception) {
            paymentLogger.error(
                "Failed to save verified payment for user=$userEmail order=$orderId",
                e
            )
            false
        }

        if (!saved) {
            call.respondError(
                message = "Failed to store payment details",
                data = Unit,
                status = HttpStatusCode.InternalServerError
            )
            return@post
        }

        // Update user's subscription history and active status
        val updatedExisting = user.subscriptions.map {
            if (it.status == SubscriptionStatus.ACTIVE) {
                it.copy(status = SubscriptionStatus.EXPIRED)
            } else it
        }
        val newSubscription = Subscription(
            service = ServiceType.PREMIUM_ONE,
            startDate = startDate,
            endDate = endDate,
            status = SubscriptionStatus.ACTIVE,
            sourcePaymentId = payment.id
        )
        val updatedUser = user.copy(
            subscriptions = updatedExisting + newSubscription,
            isPremium = true
        )

        val userUpdated = try {
            DatabaseFactory.updateUser(updatedUser)
        } catch (e: Exception) {
            paymentLogger.error("Failed to update user subscription for user=$userEmail", e)
            false
        }

        if (!userUpdated) {
            call.respondError(
                message = "Payment stored but failed to update subscription",
                data = Unit,
                status = HttpStatusCode.InternalServerError
            )
            return@post
        }

        call.respondSuccess(
            message = "Payment verified, stored and subscription activated",
            data = VerifyPaymentResponse(verified = true),
            status = HttpStatusCode.OK
        )
    }
}
