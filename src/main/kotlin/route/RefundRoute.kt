package bose.ankush.route

import bose.ankush.data.model.*
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import domain.model.Result
import domain.service.RefundService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.AuthHelper.getAuthenticatedAdminOrRespond

private val refundLogger = LoggerFactory.getLogger("RefundRoute")

/**
 * Refund routes for admin operations and webhook handling
 * - Admin refund initiation
 * - Refund queries and history
 * - Refund analytics and metrics
 * - CSV export
 * - Razorpay webhook handling
 */
fun Route.refundRoute() {
    val refundService: RefundService by application.inject()

    // Admin refund routes - require admin authentication
    route("/refunds") {

        // POST /refunds/initiate - Initiate a refund
        post("/initiate") {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@post
            refundLogger.info("Admin ${admin.email} initiating refund")

            val request = try {
                call.receive<InitiateRefundRequest>()
            } catch (e: Exception) {
                refundLogger.warn("Malformed refund request payload", e)
                call.respondError(
                    message = "Malformed JSON payload",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            // Log the received request for debugging
            refundLogger.info("Refund request received: paymentId=${request.paymentId}, amount=${request.amount}, reason=${request.reason}")

            // Validate request
            if (request.paymentId.isBlank()) {
                call.respondError(
                    message = "Payment ID is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            if (request.amount != null && request.amount <= 0) {
                call.respondError(
                    message = "Refund amount must be greater than zero (amount is in paise)",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@post
            }

            when (val result = refundService.initiateRefund(admin.email, request)) {
                is Result.Success -> {
                    refundLogger.info("Refund initiated successfully: ${result.data.refund?.refundId}")
                    call.respondSuccess(
                        message = result.data.message,
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    refundLogger.warn("Refund initiation failed: ${result.message}")
                    val statusCode = when {
                        result.message.contains("not found", ignoreCase = true) -> HttpStatusCode.NotFound
                        result.message.contains("already", ignoreCase = true) -> HttpStatusCode.BadRequest
                        result.message.contains("exceed", ignoreCase = true) -> HttpStatusCode.BadRequest
                        result.message.contains("insufficient", ignoreCase = true) -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // GET /refunds/{refundId} - Get refund details
        get("/{refundId}") {
            call.getAuthenticatedAdminOrRespond() ?: return@get

            val refundId = call.parameters["refundId"]?.trim()
            if (refundId.isNullOrBlank()) {
                call.respondError(
                    message = "Refund ID is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            when (val result = refundService.getRefund(refundId)) {
                is Result.Success -> {
                    call.respondSuccess(
                        message = "Refund details retrieved",
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    val statusCode = if (result.message.contains("not found", ignoreCase = true)) {
                        HttpStatusCode.NotFound
                    } else {
                        HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // GET /refunds/payment/{paymentId} - Get all refunds for a payment
        get("/payment/{paymentId}") {
            call.getAuthenticatedAdminOrRespond() ?: return@get

            val paymentId = call.parameters["paymentId"]?.trim()
            if (paymentId.isNullOrBlank()) {
                call.respondError(
                    message = "Payment ID is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            when (val result = refundService.getRefundsForPayment(paymentId)) {
                is Result.Success -> {
                    call.respondSuccess(
                        message = "Payment refund summary retrieved",
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    val statusCode = if (result.message.contains("not found", ignoreCase = true)) {
                        HttpStatusCode.NotFound
                    } else {
                        HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // GET /refunds/payment/{paymentId}/check - Check refund status from Razorpay
        get("/payment/{paymentId}/check") {
            call.getAuthenticatedAdminOrRespond() ?: return@get

            val paymentId = call.parameters["paymentId"]?.trim()
            if (paymentId.isNullOrBlank()) {
                call.respondError(
                    message = "Payment ID is required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            when (val result = refundService.checkPaymentRefundStatus(paymentId)) {
                is Result.Success -> {
                    call.respondSuccess(
                        message = "Payment refund status checked from Razorpay",
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    val statusCode = if (result.message.contains("not found", ignoreCase = true)) {
                        HttpStatusCode.NotFound
                    } else {
                        HttpStatusCode.InternalServerError
                    }
                    call.respondError(result.message, Unit, statusCode)
                }
            }
        }

        // GET /refunds/history - Get refund history with pagination and filtering
        get("/history") {
            call.getAuthenticatedAdminOrRespond() ?: return@get

            // Parse query parameters
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
            val statusParam = call.request.queryParameters["status"]
            val startDate = call.request.queryParameters["startDate"]
            val endDate = call.request.queryParameters["endDate"]

            // Validate pagination parameters
            if (page < 1) {
                call.respondError(
                    message = "Page number must be greater than 0",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            if (pageSize < 1 || pageSize > 100) {
                call.respondError(
                    message = "Page size must be between 1 and 100",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            // Parse status filter
            val status = if (statusParam != null) {
                try {
                    RefundStatus.valueOf(statusParam.uppercase())
                } catch (_: IllegalArgumentException) {
                    call.respondError(
                        message = "Invalid status value. Must be one of: PENDING, PROCESSED, FAILED",
                        data = Unit,
                        status = HttpStatusCode.BadRequest
                    )
                    return@get
                }
            } else {
                null
            }

            when (val result = refundService.getRefundHistory(page, pageSize, status, startDate, endDate)) {
                is Result.Success -> {
                    call.respondSuccess(
                        message = "Refund history retrieved",
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    call.respondError(result.message, Unit, HttpStatusCode.InternalServerError)
                }
            }
        }

        // GET /refunds/metrics - Get refund metrics for dashboard
        get("/metrics") {
            call.getAuthenticatedAdminOrRespond() ?: return@get

            when (val result = refundService.getRefundMetrics()) {
                is Result.Success -> {
                    call.respondSuccess(
                        message = "Refund metrics retrieved",
                        data = result.data,
                        status = HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    call.respondError(result.message, Unit, HttpStatusCode.InternalServerError)
                }
            }
        }

        // GET /refunds/export - Export refunds to CSV
        get("/export") {
            call.getAuthenticatedAdminOrRespond() ?: return@get

            val startDate = call.request.queryParameters["startDate"]
            val endDate = call.request.queryParameters["endDate"]

            if (startDate.isNullOrBlank() || endDate.isNullOrBlank()) {
                call.respondError(
                    message = "Both startDate and endDate are required",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
                return@get
            }

            when (val result = refundService.exportRefunds(startDate, endDate)) {
                is Result.Success -> {
                    // Return CSV as text/csv content type
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"refunds_${startDate}_to_${endDate}.csv\""
                    )
                    call.respondText(
                        result.data,
                        ContentType.Text.CSV,
                        HttpStatusCode.OK
                    )
                }

                is Result.Error -> {
                    call.respondError(result.message, Unit, HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    // Webhook routes - no authentication required (verified via signature)
    route("/webhooks/razorpay") {

        // POST /webhooks/razorpay/refund - Handle Razorpay refund webhooks
        post("/refund") {
            try {
                refundLogger.info("Received Razorpay refund webhook")

                // Extract signature from header
                val signature = call.request.headers["X-Razorpay-Signature"]
                if (signature.isNullOrBlank()) {
                    refundLogger.warn("Webhook received without X-Razorpay-Signature header")
                    call.respondError(
                        message = "Missing webhook signature",
                        data = Unit,
                        status = HttpStatusCode.Unauthorized
                    )
                    return@post
                }

                // Get raw payload as string
                val payload = try {
                    call.receiveText()
                } catch (e: Exception) {
                    refundLogger.error("Failed to read webhook payload body", e)
                    call.respondError(
                        message = "Failed to read webhook payload",
                        data = Unit,
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }

                if (payload.isBlank()) {
                    refundLogger.warn("Webhook received with empty payload body")
                    call.respondError(
                        message = "Empty webhook payload",
                        data = Unit,
                        status = HttpStatusCode.BadRequest
                    )
                    return@post
                }

                refundLogger.debug("Webhook payload size: ${payload.length} bytes")

                // Process webhook
                when (val result = refundService.handleRefundWebhook(signature, payload)) {
                    is Result.Success -> {
                        refundLogger.info("Webhook processed successfully")
                        call.respondSuccess(
                            message = "Webhook processed successfully",
                            data = true,
                            status = HttpStatusCode.OK
                        )
                    }

                    is Result.Error -> {
                        refundLogger.error("Webhook processing failed: ${result.message}")
                        val statusCode = if (result.message.contains("signature", ignoreCase = true) ||
                            result.message.contains("unauthorized", ignoreCase = true)
                        ) {
                            HttpStatusCode.Unauthorized
                        } else if (result.message.contains("parse", ignoreCase = true) ||
                            result.message.contains("invalid", ignoreCase = true)
                        ) {
                            HttpStatusCode.BadRequest
                        } else {
                            HttpStatusCode.InternalServerError
                        }
                        call.respondError(result.message, Unit, statusCode)
                    }
                }
            } catch (e: Exception) {
                refundLogger.error("Unexpected exception in webhook endpoint", e)
                call.respondError(
                    message = "Internal server error processing webhook",
                    data = Unit,
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
    }
}
