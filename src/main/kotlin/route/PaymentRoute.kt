package bose.ankush.route

import bose.ankush.data.db.DatabaseFactory
import bose.ankush.data.model.*
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import bose.ankush.util.SignatureUtil
import bose.ankush.util.getSecretValue
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import util.AuthHelper.getAuthenticatedUserOrRespond
import util.Constants
import java.util.*

private val paymentLogger = LoggerFactory.getLogger("PaymentRoute")
private val razorpayClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
}

private val relaxedJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Fetches order details from Razorpay API
 * @param orderId The Razorpay order ID
 * @param keyId Razorpay API key ID
 * @param keySecret Razorpay API key secret
 * @return RazorpayOrderDetailsResponse containing order information or null if fetch fails
 */
private suspend fun fetchRazorpayOrderDetails(
    orderId: String,
    keyId: String,
    keySecret: String
): RazorpayOrderDetailsResponse? {
    return try {
        val basic = Base64.getEncoder().encodeToString("$keyId:$keySecret".toByteArray())
        val response: HttpResponse = razorpayClient.get("https://api.razorpay.com/v1/orders/$orderId") {
            headers.append(HttpHeaders.Authorization, "Basic $basic")
        }

        val bodyText = response.bodyAsText()
        if (response.status.value in 200..299) {
            try {
                relaxedJson.decodeFromString(
                    RazorpayOrderDetailsResponse.serializer(),
                    bodyText
                )
            } catch (e: Exception) {
                paymentLogger.warn("Failed to parse order details response for $orderId", e)
                null
            }
        } else {
            paymentLogger.warn("Failed to fetch order details for $orderId: ${response.status}")
            null
        }
    } catch (e: Exception) {
        paymentLogger.warn("Exception while fetching order details for $orderId", e)
        null
    }
}



/** Payment routes: create order and verify payment */
fun Route.paymentRoute() {
    // POST /create-order -> Create Razorpay order server-side
    post(Constants.Api.CREATE_ORDER_ENDPOINT) {
        call.getAuthenticatedUserOrRespond() ?: return@post

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
        val user = call.getAuthenticatedUserOrRespond() ?: return@post

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

        // Use authenticated user's email
        val userEmail = user.email

        // Fetch order details from Razorpay to get amount, currency, and receipt
        val keyId = getSecretValue(Constants.Auth.RAZORPAY_KEY_ID)
        val orderDetails = fetchRazorpayOrderDetails(orderId, keyId, secret)

        if (orderDetails == null) {
            paymentLogger.warn("Could not fetch order details for $orderId, proceeding without amount data")
        }

        // Persist payment and update subscription
        // Fetch user by email to link payment and update subscriptions
        val dbUser = try {
            DatabaseFactory.findUserByEmail(userEmail)
        } catch (e: Exception) {
            paymentLogger.error("Failed to fetch user by email for payment storing: $userEmail", e)
            null
        }

        if (dbUser == null) {
            call.respondError(
                message = "User not found for payment processing",
                data = Unit,
                status = HttpStatusCode.NotFound
            )
            return@post
        }

        // Determine subscription window using ServiceTypeResolver
        // Fetch duration from service catalog for dynamic configuration
        val serviceType = ServiceType.PREMIUM_ONE
        val duration = util.ServiceTypeResolver.getDefaultDurationInSeconds(serviceType) ?: (30L * 24L * 60L * 60L)
        val nowInstant = java.time.Instant.now()
        val startDate = nowInstant.toString()
        val endDate = nowInstant.plusSeconds(duration).toString()

        // Admin tracking context
        val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
            ?: call.request.headers["X-Real-IP"]
        val ua = call.request.headers["User-Agent"]

        // Create payment record
        // ServiceType enum is maintained for backward compatibility
        // ServiceTypeResolver provides access to full service configuration
        // Resolve service configuration to get pricing details
        val serviceConfig = when (val result = util.ServiceTypeResolver.resolveServiceType(serviceType)) {
            is domain.model.Result.Success -> result.data
            is domain.model.Result.Error -> {
                paymentLogger.warn("Could not resolve service configuration for $serviceType: ${result.message}")
                null
            }
        }

        val payment = Payment(
            userEmail = userEmail,
            orderId = orderId,
            paymentId = paymentId,
            signature = signature,
            amount = orderDetails?.amount,
            currency = orderDetails?.currency ?: serviceConfig?.pricingTiers?.firstOrNull()?.currency,
            receipt = orderDetails?.receipt,
            status = "verified",
            verifiedAt = startDate,
            userId = dbUser.id.toHexString(),
            serviceType = serviceType,
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
        val updatedExisting = dbUser.subscriptions.map {
            if (it.status == SubscriptionStatus.ACTIVE) {
                it.copy(status = SubscriptionStatus.EXPIRED)
            } else it
        }
        // Create new subscription
        // ServiceType enum is maintained for backward compatibility
        // ServiceTypeResolver provides access to full service configuration
        // The subscription uses the resolved duration and service details
        val newSubscription = Subscription(
            service = serviceType,
            startDate = startDate,
            endDate = endDate,
            status = SubscriptionStatus.ACTIVE,
            sourcePaymentId = payment.id
        )
        val updatedUser = dbUser.copy(
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
