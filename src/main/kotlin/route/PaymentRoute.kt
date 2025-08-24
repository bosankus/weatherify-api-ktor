package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory
import bose.ankush.data.model.CreateOrderRequest
import bose.ankush.data.model.CreateOrderResponse
import bose.ankush.data.model.Payment
import bose.ankush.data.model.RazorpayCreateOrderPayload
import bose.ankush.data.model.RazorpayErrorResponse
import bose.ankush.data.model.VerifyPaymentRequest
import bose.ankush.data.model.VerifyPaymentResponse
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import bose.ankush.util.SignatureUtil
import bose.ankush.util.getSecretValue
import config.JwtConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import util.Constants
import java.util.Base64

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
                    bose.ankush.data.model.RazorpayOrderResponse.serializer(),
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

        // Persist payment
        val payment = Payment(
            userEmail = userEmail,
            orderId = orderId,
            paymentId = paymentId,
            signature = signature,
            status = "verified"
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

        call.respondSuccess(
            message = "Payment verified and stored successfully",
            data = VerifyPaymentResponse(verified = true),
            status = HttpStatusCode.OK
        )
    }
}
