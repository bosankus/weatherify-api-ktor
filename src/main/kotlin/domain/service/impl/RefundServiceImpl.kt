package domain.service.impl

import bose.ankush.data.model.*
import bose.ankush.util.getSecretValue
import domain.model.Result
import domain.repository.PaymentRepository
import domain.repository.RefundRepository
import domain.repository.UserRepository
import domain.service.EmailService
import domain.service.RefundService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import util.Constants
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Implementation of RefundService that handles refund operations. */
class RefundServiceImpl(
    private val refundRepository: RefundRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val subscriptionService: domain.service.SubscriptionService,
    private val notificationService: domain.service.NotificationService
) : RefundService {

    private val logger = LoggerFactory.getLogger(RefundServiceImpl::class.java)

    // Razorpay API configuration
    private val razorpayKeyId: String by lazy { getSecretValue(Constants.Auth.RAZORPAY_KEY_ID) }
    private val razorpaySecret: String by lazy { getSecretValue(Constants.Auth.RAZORPAY_SECRET) }
    private val razorpayBaseUrl = "https://api.razorpay.com/v1"

    // JSON parser for Razorpay responses
    private val razorpayJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    // HTTP client for Razorpay API calls
    private val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(razorpayJson)
            }
        }

    /**
     * Preprocesses Razorpay JSON to fix inconsistencies.
     * Razorpay sends empty arrays [] instead of empty objects {} or null for map fields.
     * This function converts [] to null to avoid deserialization errors.
     *
     * Note: This handles both single objects and arrays of objects (e.g., refunds list)
     */
    private fun preprocessRazorpayJson(json: String): String {
        var processed = json

        // Replace all occurrences of empty array for notes field
        processed = processed.replace(""""notes":\s*\[\]""".toRegex(), """"notes":null""")

        // Replace all occurrences of empty array for acquirer_data field
        processed = processed.replace(""""acquirer_data":\s*\[\]""".toRegex(), """"acquirer_data":null""")

        return processed
    }

    /** Initiate a refund through Razorpay API. */
    override suspend fun initiateRefund(
        adminEmail: String,
        request: InitiateRefundRequest
    ): Result<RefundResponse> {
        logger.info("Initiating refund for payment ${request.paymentId} by admin $adminEmail")

        return try {
            // 1. Validate payment exists
            val paymentResult = paymentRepository.getPaymentByTransactionId(request.paymentId)
            if (paymentResult is Result.Error) {
                return Result.success(
                    RefundResponse(
                        success = false,
                        message = "Failed to find payment: ${paymentResult.message}"
                    )
                )
            }

            val payment = (paymentResult as Result.Success).data
            if (payment == null) {
                return Result.success(
                    RefundResponse(
                        success = false,
                        message = "Payment not found: ${request.paymentId}"
                    )
                )
            }

            // 2. Check refundable amount
            val paymentAmount =
                payment.amount
                    ?: return Result.success(
                        RefundResponse(
                            success = false,
                            message = "Payment amount is not available"
                        )
                    )

            val totalRefundedResult = refundRepository.getTotalRefundedForPayment(request.paymentId)
            val totalRefunded =
                if (totalRefundedResult is Result.Success) {
                    totalRefundedResult.data
                } else {
                    0
                }

            val remainingRefundable = paymentAmount - totalRefunded

            if (remainingRefundable <= 0) {
                return Result.success(
                    RefundResponse(
                        success = false,
                        message = "This payment has already been fully refunded"
                    )
                )
            }

            val refundAmount = request.amount ?: paymentAmount

            if (refundAmount > remainingRefundable) {
                return Result.success(
                    RefundResponse(
                        success = false,
                        message =
                            "Refund amount cannot exceed remaining refundable amount. Requested: ${refundAmount / 100.0}, Available: ${remainingRefundable / 100.0}"
                    )
                )
            }

            if (refundAmount <= 0) {
                return Result.success(
                    RefundResponse(
                        success = false,
                        message = "Refund amount must be greater than zero"
                    )
                )
            }

            // 3. Call Razorpay API
            // Razorpay requires amount to be specified (in paise), even for full refunds
            val razorpayRequest =
                RazorpayRefundRequest(
                    amount = refundAmount,  // Always send the amount in paise
                    speed =
                        when (request.speed) {
                            RefundSpeed.OPTIMUM -> "optimum"
                            RefundSpeed.NORMAL -> "normal"
                        },
                    notes = if (request.notes.isNullOrBlank()) null else mapOf("comment" to request.notes),
                    receipt = request.receipt
                )

            logger.info("Initiating refund for payment ${request.paymentId}, amount=${refundAmount / 100.0} (${refundAmount} paise)")
            val razorpayResponse = callRazorpayRefundAPI(request.paymentId, razorpayRequest)

            // 4. Map response to internal model
            val refund =
                mapRazorpayResponseToRefund(
                    response = razorpayResponse,
                    adminEmail = adminEmail,
                    userEmail = payment.userEmail,
                    userId = payment.userId,
                    reason = request.reason
                )

            // 5. Store refund record
            val createResult = refundRepository.createRefund(refund)
            if (createResult is Result.Error) {
                logger.error("Failed to store refund record: ${createResult.message}")
                return Result.success(
                    RefundResponse(
                        success = false,
                        message =
                            "Refund initiated but failed to store record: ${createResult.message}"
                    )
                )
            }

            // 6. Send notification email and push notification (don't fail if notifications fail)
            try {
                // Send email notification
                emailService.sendRefundNotification(
                    userEmail = payment.userEmail,
                    refundId = refund.refundId,
                    amount = refund.amount / 100.0, // Convert paise to rupees
                    paymentId = refund.paymentId,
                    status = "initiated"
                )
                logger.info("Refund notification email sent to ${payment.userEmail}")

                // Send push notification
                sendRefundPushNotification(
                    userEmail = payment.userEmail,
                    amount = refund.amount / 100.0,
                    status = "initiated"
                )
            } catch (e: Exception) {
                logger.warn("Failed to send refund notifications", e)
            }

            // 7. Automatically cancel subscription after successful refund with retry logic
            var subscriptionCancelled = false
            var subscriptionCancellationMessage = ""
            var attemptCount = 0
            val maxAttempts = 2

            while (attemptCount < maxAttempts && !subscriptionCancelled) {
                attemptCount++
                try {
                    logger.info("Attempting to cancel subscription for user ${payment.userEmail} after refund (attempt $attemptCount/$maxAttempts)")
                    val cancelResult = subscriptionService.cancelUserSubscription(
                        adminEmail = adminEmail,
                        targetUserEmail = payment.userEmail
                    )

                    when (cancelResult) {
                        is Result.Success -> {
                            subscriptionCancelled = true
                            subscriptionCancellationMessage = "Subscription cancelled successfully"
                            logger.info("Subscription cancelled successfully for ${payment.userEmail} after refund ${refund.refundId} on attempt $attemptCount")
                        }

                        is Result.Error -> {
                            subscriptionCancellationMessage = "Failed to cancel subscription: ${cancelResult.message}"
                            logger.warn("Failed to cancel subscription after refund on attempt $attemptCount: ${cancelResult.message}")

                            // If this is not the last attempt, wait briefly before retrying
                            if (attemptCount < maxAttempts) {
                                logger.info("Retrying subscription cancellation...")
                                kotlinx.coroutines.delay(500) // Wait 500ms before retry
                            }
                        }
                    }
                } catch (e: Exception) {
                    subscriptionCancellationMessage = "Exception during subscription cancellation: ${e.message}"
                    logger.warn("Exception while cancelling subscription after refund on attempt $attemptCount", e)

                    // If this is not the last attempt, wait briefly before retrying
                    if (attemptCount < maxAttempts) {
                        logger.info("Retrying subscription cancellation after exception...")
                        kotlinx.coroutines.delay(500) // Wait 500ms before retry
                    }
                }
            }

            // Build response message based on subscription cancellation result
            val responseMessage = if (subscriptionCancelled) {
                "Refund initiated successfully and subscription cancelled"
            } else {
                "Refund initiated successfully but subscription cancellation failed after $maxAttempts attempts. Please cancel subscription manually. Reason: $subscriptionCancellationMessage"
            }

            // Log final status
            if (!subscriptionCancelled) {
                logger.error("Failed to cancel subscription for ${payment.userEmail} after $maxAttempts attempts. Refund ${refund.refundId} was successful but subscription remains active.")
            }

            logger.info("Refund initiated successfully: ${refund.refundId}. Subscription cancelled: $subscriptionCancelled")

            Result.success(
                RefundResponse(
                    success = true,
                    message = responseMessage,
                    refund = mapRefundToDto(refund)
                )
            )
        } catch (e: RazorpayApiException) {
            // Handle Razorpay-specific errors with user-friendly messages
            logger.error("Razorpay API error during refund initiation: ${e.message}")
            Result.success(
                RefundResponse(
                    success = false,
                    message = e.message ?: "Razorpay API error"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to initiate refund", e)
            Result.success(
                RefundResponse(
                    success = false,
                    message = "Failed to initiate refund: ${e.message}"
                )
            )
        }
    }

    /** Call Razorpay refund API. */
    private suspend fun callRazorpayRefundAPI(
        paymentId: String,
        request: RazorpayRefundRequest
    ): RazorpayRefundResponse {
        logger.info("Calling Razorpay refund API for payment $paymentId")
        logger.debug("Refund request details: amount=${request.amount}, speed=${request.speed}, notes=${request.notes}, receipt=${request.receipt}")

        try {
            val response =
                httpClient.post("$razorpayBaseUrl/payments/$paymentId/refund") {
                    // Basic Auth with key_id:key_secret
                    val credentials = "$razorpayKeyId:$razorpaySecret"
                    val encodedCredentials =
                        Base64.getEncoder().encodeToString(credentials.toByteArray())
                    header(HttpHeaders.Authorization, "Basic $encodedCredentials")

                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.isSuccess()) {
                // Get response as string first to preprocess it
                val responseBody = response.body<String>()

                // Preprocess JSON to fix Razorpay's inconsistent format ([] instead of {} or null)
                val preprocessedBody = preprocessRazorpayJson(responseBody)

                // Parse the preprocessed JSON
                return razorpayJson.decodeFromString<RazorpayRefundResponse>(preprocessedBody)
            } else {
                val errorBody = response.body<String>()
                logger.error("Razorpay API error: ${response.status} - $errorBody")

                // Parse Razorpay error and provide user-friendly message
                val userFriendlyMessage = parseRazorpayError(errorBody)
                throw RazorpayApiException(userFriendlyMessage, errorBody)
            }
        } catch (e: RazorpayApiException) {
            // Re-throw Razorpay-specific exceptions
            throw e
        } catch (e: Exception) {
            logger.error("Failed to call Razorpay API", e)
            throw Exception("Failed to call Razorpay API: ${e.message}", e)
        }
    }

    /** Parse Razorpay error response and return user-friendly message */
    private fun parseRazorpayError(errorBody: String): String {
        return try {
            // Try to parse the error JSON and extract description
            val errorJson = razorpayJson.decodeFromString<JsonObject>(errorBody)
            val errorObj = errorJson["error"] as? JsonObject

            if (errorObj != null) {
                val description = (errorObj["description"] as? JsonPrimitive)?.content
                val code = (errorObj["code"] as? JsonPrimitive)?.content
                val field = (errorObj["field"] as? JsonPrimitive)?.content

                // Build user-friendly error message
                return when {
                    description != null && field != null -> "$description (Field: $field)"
                    description != null -> description
                    code != null -> "Razorpay error: $code"
                    else -> "Razorpay API error"
                }
            }

            // Fallback: try regex extraction
            val descriptionRegex = """"description"\s*:\s*"([^"]+)"""".toRegex()
            val match = descriptionRegex.find(errorBody)
            match?.groupValues?.get(1) ?: "Razorpay API error: $errorBody"
        } catch (e: Exception) {
            logger.warn("Failed to parse Razorpay error response", e)
            "Razorpay API error: $errorBody"
        }
    }

    /** Custom exception for Razorpay API errors */
    private class RazorpayApiException(message: String, val rawError: String) : Exception(message)

    /** Map Razorpay response to internal Refund model. */
    private fun mapRazorpayResponseToRefund(
        response: RazorpayRefundResponse,
        adminEmail: String,
        userEmail: String,
        userId: String?,
        reason: String?
    ): Refund {
        val status =
            when (response.status.lowercase()) {
                "pending" -> RefundStatus.PENDING
                "processed" -> RefundStatus.PROCESSED
                "failed" -> RefundStatus.FAILED
                else -> RefundStatus.PENDING
            }

        val speedRequested =
            when (response.speed_requested.lowercase()) {
                "optimum" -> RefundSpeed.OPTIMUM
                "normal" -> RefundSpeed.NORMAL
                else -> RefundSpeed.NORMAL
            }

        val speedProcessed =
            when (response.speed_processed.lowercase()) {
                "instant" -> RefundSpeed.OPTIMUM
                "normal" -> RefundSpeed.NORMAL
                else -> null
            }

        val createdAt = Instant.ofEpochSecond(response.created_at).toString()
        val processedAt = if (status == RefundStatus.PROCESSED) createdAt else null
        val failedAt = if (status == RefundStatus.FAILED) createdAt else null

        return Refund(
            refundId = response.id,
            paymentId = response.payment_id,
            orderId = null, // Not provided in Razorpay response
            amount = response.amount,
            currency = response.currency,
            status = status,
            speedRequested = speedRequested,
            speedProcessed = speedProcessed,
            userEmail = userEmail,
            userId = userId,
            processedBy = adminEmail,
            reason = reason,
            notes = response.notes?.get("comment"), // Extract comment from Razorpay notes map (null-safe)
            receipt = response.receipt,
            createdAt = createdAt,
            processedAt = processedAt,
            failedAt = failedAt,
            acquirerData = response.acquirer_data,
            batchId = response.batch_id,
            errorCode = null,
            errorDescription = null
        )
    }

    /** Map internal Refund model to DTO. */
    private fun mapRefundToDto(refund: Refund): RefundDto {
        return RefundDto(
            refundId = refund.refundId,
            paymentId = refund.paymentId,
            amount = refund.amount / 100.0, // Convert paise to rupees
            currency = refund.currency,
            status = refund.status,
            speedRequested = refund.speedRequested,
            speedProcessed = refund.speedProcessed,
            userEmail = refund.userEmail,
            processedBy = refund.processedBy,
            reason = refund.reason,
            createdAt = refund.createdAt,
            processedAt = refund.processedAt
        )
    }

    /** Get refund details by refund ID. */
    override suspend fun getRefund(refundId: String): Result<RefundDto> {
        logger.debug("Getting refund details for $refundId")

        return try {
            val refundResult = refundRepository.getRefundById(refundId)

            if (refundResult is Result.Error) {
                return Result.error("Failed to get refund: ${refundResult.message}")
            }

            val refund = (refundResult as Result.Success).data
            if (refund == null) {
                return Result.error("Refund not found: $refundId")
            }

            Result.success(mapRefundToDto(refund))
        } catch (e: Exception) {
            logger.error("Failed to get refund $refundId", e)
            Result.error("Failed to get refund: ${e.message}", e)
        }
    }

    /** Get all refunds for a payment with summary. */
    override suspend fun getRefundsForPayment(paymentId: String): Result<PaymentRefundSummary> {
        logger.debug("Getting refunds for payment $paymentId")

        return try {
            // Get payment details
            val paymentResult = paymentRepository.getPaymentByTransactionId(paymentId)
            if (paymentResult is Result.Error) {
                return Result.error("Failed to find payment: ${paymentResult.message}")
            }

            val payment = (paymentResult as Result.Success).data
            if (payment == null) {
                return Result.error("Payment not found: $paymentId")
            }

            // Get all refunds for this payment
            val refundsResult = refundRepository.getRefundsByPaymentId(paymentId)
            if (refundsResult is Result.Error) {
                return Result.error("Failed to get refunds: ${refundsResult.message}")
            }

            val refunds = (refundsResult as Result.Success).data
            val refundDtos = refunds.map { mapRefundToDto(it) }

            // Calculate totals
            val paymentAmount = payment.amount ?: 0
            val totalRefunded = refunds.sumOf { it.amount }
            val remainingRefundable = paymentAmount - totalRefunded
            val isFullyRefunded = remainingRefundable <= 0

            val summary =
                PaymentRefundSummary(
                    paymentId = paymentId,
                    originalAmount = paymentAmount,
                    totalRefunded = totalRefunded,
                    remainingRefundable = remainingRefundable,
                    refunds = refundDtos,
                    isFullyRefunded = isFullyRefunded
                )

            Result.success(summary)
        } catch (e: Exception) {
            logger.error("Failed to get refunds for payment $paymentId", e)
            Result.error("Failed to get refunds for payment: ${e.message}", e)
        }
    }

    /** Check refund status from Razorpay API for a payment. */
    override suspend fun checkPaymentRefundStatus(paymentId: String): Result<PaymentRefundSummary> {
        logger.debug("Checking refund status from Razorpay for payment $paymentId")

        return try {
            // Get payment details
            val paymentResult = paymentRepository.getPaymentByTransactionId(paymentId)
            if (paymentResult is Result.Error) {
                return Result.error("Failed to find payment: ${paymentResult.message}")
            }

            val payment = (paymentResult as Result.Success).data
            if (payment == null) {
                return Result.error("Payment not found: $paymentId")
            }

            // Call Razorpay API to get refunds for this payment
            val razorpayRefunds = try {
                fetchRazorpayRefunds(paymentId)
            } catch (e: Exception) {
                logger.warn("Failed to fetch refunds from Razorpay: ${e.message}")
                // If Razorpay API fails, fall back to local database
                return getRefundsForPayment(paymentId)
            }

            // Sync refunds with local database
            for (razorpayRefund in razorpayRefunds) {
                val existingRefund = refundRepository.getRefundById(razorpayRefund.id)
                if (existingRefund is Result.Success && existingRefund.data == null) {
                    // Refund doesn't exist in our database, create it
                    val refund = mapRazorpayResponseToRefund(
                        response = razorpayRefund,
                        adminEmail = "system",
                        userEmail = payment.userEmail,
                        userId = payment.userId,
                        reason = "Auto-synced from Razorpay"
                    )
                    refundRepository.createRefund(refund)
                    logger.info("Synced refund ${razorpayRefund.id} from Razorpay")
                }
            }

            // Return updated summary from database
            getRefundsForPayment(paymentId)
        } catch (e: Exception) {
            logger.error("Failed to check refund status for payment $paymentId", e)
            Result.error("Failed to check refund status: ${e.message}", e)
        }
    }

    /** Fetch refunds from Razorpay API for a payment. */
    private suspend fun fetchRazorpayRefunds(paymentId: String): List<RazorpayRefundResponse> {
        logger.debug("Fetching refunds from Razorpay for payment $paymentId")

        try {
            val response = httpClient.get("$razorpayBaseUrl/payments/$paymentId/refunds") {
                val credentials = "$razorpayKeyId:$razorpaySecret"
                val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
                header(HttpHeaders.Authorization, "Basic $encodedCredentials")
            }

            if (response.status.isSuccess()) {
                // Get response as string first to preprocess it
                val responseBody = response.body<String>()
                logger.debug("Razorpay refunds response size: ${responseBody.length} bytes")

                // Preprocess JSON to fix Razorpay's inconsistent format ([] instead of {} or null)
                val preprocessedBody = preprocessRazorpayJson(responseBody)

                // Parse the preprocessed JSON
                val refundsResponse = razorpayJson.decodeFromString<RazorpayRefundsListResponse>(preprocessedBody)
                logger.info("Fetched ${refundsResponse.count} refunds from Razorpay for payment $paymentId")
                return refundsResponse.items
            } else {
                val errorBody = response.body<String>()
                logger.error("Razorpay API error: ${response.status} - $errorBody")
                throw Exception("Razorpay API error: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch refunds from Razorpay for payment $paymentId", e)
            throw e
        }
    }

    /** Get refund history with pagination and filtering. */
    override suspend fun getRefundHistory(
        page: Int,
        pageSize: Int,
        status: RefundStatus?,
        startDate: String?,
        endDate: String?
    ): Result<RefundHistoryResponse> {
        logger.debug("Getting refund history: page=$page, pageSize=$pageSize, status=$status")

        return try {
            val refundsResult =
                refundRepository.getAllRefunds(
                    page = page,
                    pageSize = pageSize,
                    status = status,
                    startDate = startDate,
                    endDate = endDate
                )

            if (refundsResult is Result.Error) {
                return Result.error("Failed to get refund history: ${refundsResult.message}")
            }

            val (refunds, totalCount) = (refundsResult as Result.Success).data
            val refundDtos = refunds.map { mapRefundToDto(it) }

            val pagination =
                PaginationInfo(
                    page = page,
                    pageSize = pageSize,
                    totalCount = totalCount,
                    totalPages = ((totalCount + pageSize - 1) / pageSize).toInt()
                )

            val response = RefundHistoryResponse(refunds = refundDtos, pagination = pagination)

            Result.success(response)
        } catch (e: Exception) {
            logger.error("Failed to get refund history", e)
            Result.error("Failed to get refund history: ${e.message}", e)
        }
    }

    /** Get refund metrics for dashboard analytics. */
    override suspend fun getRefundMetrics(): Result<RefundMetrics> {
        logger.debug("Getting refund metrics")

        return try {
            // Get total refunded amount
            val totalRefundsResult = refundRepository.getTotalRefundedAmount()
            val totalRefunds =
                if (totalRefundsResult is Result.Success) {
                    totalRefundsResult.data / 100.0 // Convert paise to rupees
                } else {
                    0.0
                }

            // Get monthly refunded amount
            val currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val monthlyRefundsResult = refundRepository.getMonthlyRefundedAmount(currentMonth)
            val monthlyRefunds =
                if (monthlyRefundsResult is Result.Success) {
                    monthlyRefundsResult.data / 100.0 // Convert paise to rupees
                } else {
                    0.0
                }

            // Get total revenue for refund rate calculation
            val totalRevenueResult = paymentRepository.getTotalRevenue()
            val totalRevenue =
                if (totalRevenueResult is Result.Success) {
                    totalRevenueResult.data
                } else {
                    0.0
                }

            // Calculate refund rate
            val refundRate =
                if (totalRevenue > 0) {
                    (totalRefunds / totalRevenue) * 100
                } else {
                    0.0
                }

            // Get refund counts by speed
            val speedCountsResult = refundRepository.getRefundCountBySpeed()
            val (instantCount, normalCount) =
                if (speedCountsResult is Result.Success) {
                    speedCountsResult.data
                } else {
                    Pair(0, 0)
                }

            // Get average processing time
            val avgProcessingTimeResult = refundRepository.getAverageProcessingTime()
            val avgProcessingTime =
                if (avgProcessingTimeResult is Result.Success) {
                    avgProcessingTimeResult.data
                } else {
                    0.0
                }

            // Get monthly refund chart data (last 12 months)
            val monthlyDataResult = refundRepository.getMonthlyRefundData(12)
            val monthlyChartData =
                if (monthlyDataResult is Result.Success) {
                    monthlyDataResult.data
                } else {
                    emptyList()
                }

            // Get total refund count (sum of instant and normal)
            val totalRefundCount = instantCount + normalCount

            // Get monthly refund count from current month data
            val monthlyRefundCount =
                monthlyChartData.find { it.month == currentMonth }?.refundCount ?: 0

            val metrics =
                RefundMetrics(
                    totalRefunds = totalRefunds,
                    monthlyRefunds = monthlyRefunds,
                    refundRate = refundRate,
                    totalRefundCount = totalRefundCount,
                    monthlyRefundCount = monthlyRefundCount,
                    instantRefundCount = instantCount,
                    normalRefundCount = normalCount,
                    averageProcessingTimeHours = avgProcessingTime,
                    monthlyRefundChart = monthlyChartData
                )

            Result.success(metrics)
        } catch (e: Exception) {
            logger.error("Failed to get refund metrics", e)
            Result.error("Failed to get refund metrics: ${e.message}", e)
        }
    }

    /** Export refunds to CSV format. */
    override suspend fun exportRefunds(startDate: String, endDate: String): Result<String> {
        logger.debug("Exporting refunds from $startDate to $endDate")

        return try {
            // Get all refunds within date range (use large page size to get all)
            val refundsResult =
                refundRepository.getAllRefunds(
                    page = 1,
                    pageSize = 100000, // Large number to get all refunds
                    status = null,
                    startDate = startDate,
                    endDate = endDate
                )

            if (refundsResult is Result.Error) {
                return Result.error("Failed to get refunds for export: ${refundsResult.message}")
            }

            val (refunds, _) = (refundsResult as Result.Success).data

            // Build CSV
            val csv = StringBuilder()

            // CSV headers
            csv.append(
                "Refund ID,Payment ID,User Email,Amount,Currency,Status,Refund Type,Reason,Processed By,Created Date,Processed Date\n"
            )

            // CSV rows
            for (refund in refunds) {
                val refundType = refund.speedProcessed?.name ?: refund.speedRequested.name
                val amount = refund.amount / 100.0 // Convert paise to rupees
                val reason = refund.reason?.replace(",", ";")?.replace("\n", " ") ?: ""
                val processedDate = refund.processedAt ?: ""

                csv.append("${refund.refundId},")
                csv.append("${refund.paymentId},")
                csv.append("${refund.userEmail},")
                csv.append("$amount,")
                csv.append("${refund.currency},")
                csv.append("${refund.status},")
                csv.append("$refundType,")
                csv.append("\"$reason\",")
                csv.append("${refund.processedBy},")
                csv.append("${refund.createdAt},")
                csv.append("$processedDate\n")
            }

            logger.info("Exported ${refunds.size} refunds to CSV")
            Result.success(csv.toString())
        } catch (e: Exception) {
            logger.error("Failed to export refunds", e)
            Result.error("Failed to export refunds: ${e.message}", e)
        }
    }

    /** Handle webhook notification from Razorpay. */
    override suspend fun handleRefundWebhook(signature: String, payload: String): Result<Boolean> {
        logger.info("Handling refund webhook - payload size: ${payload.length} bytes")

        return try {
            // 1. Verify webhook signature
            val webhookSecret =
                try {
                    getSecretValue(Constants.Auth.RAZORPAY_WEBHOOK_SECRET)
                } catch (e: Exception) {
                    logger.error("Failed to get webhook secret from GCP Secret Manager", e)
                    return Result.error("Failed to verify webhook: missing webhook secret")
                }

            logger.debug("Verifying webhook signature")
            if (!verifyWebhookSignature(signature, payload, webhookSecret)) {
                logger.warn("Invalid webhook signature received. Signature: ${signature.take(20)}...")
                return Result.error("Invalid webhook signature")
            }
            logger.debug("Webhook signature verified successfully")

            // 2. Parse webhook payload
            logger.debug("Parsing webhook payload")
            val webhookPayload =
                try {
                    // Preprocess JSON to fix Razorpay's inconsistent format ([] instead of {} or null)
                    val preprocessedPayload = preprocessRazorpayJson(payload)
                    razorpayJson.decodeFromString<RazorpayWebhookPayload>(preprocessedPayload)
                } catch (e: Exception) {
                    logger.error("Failed to parse webhook payload. Payload preview: ${payload.take(200)}", e)
                    return Result.error("Failed to parse webhook payload: ${e.message}")
                }

            val eventType = webhookPayload.event
            logger.info("Webhook event type: $eventType")

            // 3. Extract refund data
            val refundEntity = webhookPayload.payload.refund.entity
            val refundId = refundEntity.id
            logger.info("Processing webhook for refund: $refundId, event: $eventType, status: ${refundEntity.status}")

            // Determine new status based on event type and entity status
            val newStatus = when {
                eventType.contains("refund.processed", ignoreCase = true) -> RefundStatus.PROCESSED
                eventType.contains("refund.failed", ignoreCase = true) -> RefundStatus.FAILED
                eventType.contains("refund.created", ignoreCase = true) -> RefundStatus.PENDING
                else -> {
                    // Fallback to entity status if event type is unknown
                    when (refundEntity.status.lowercase()) {
                        "processed" -> RefundStatus.PROCESSED
                        "failed" -> RefundStatus.FAILED
                        "pending" -> RefundStatus.PENDING
                        else -> {
                            logger.warn("Unknown event type: $eventType and status: ${refundEntity.status}, defaulting to PENDING")
                            RefundStatus.PENDING
                        }
                    }
                }
            }

            // 4. Check if this is a duplicate webhook (status hasn't changed)
            val existingRefund = refundRepository.getRefundById(refundId)
            if (existingRefund is Result.Success && existingRefund.data != null) {
                val currentStatus = existingRefund.data.status
                if (currentStatus == newStatus) {
                    logger.info("Duplicate webhook received for refund $refundId with status $newStatus, skipping update")
                    return Result.success(true)
                }
            }

            // 5. Update refund status with appropriate timestamps
            val now = Instant.now().toString()
            val processedAt = if (newStatus == RefundStatus.PROCESSED) now else null

            logger.debug("Updating refund status in database: $refundId -> $newStatus")
            val updateResult =
                refundRepository.updateRefundStatus(
                    refundId = refundId,
                    status = newStatus,
                    processedAt = processedAt,
                    errorCode = null,
                    errorDescription = null
                )

            if (updateResult is Result.Error) {
                logger.error("Failed to update refund status in database: ${updateResult.message}")
                return Result.error("Failed to update refund status: ${updateResult.message}")
            }
            logger.info("Refund status updated successfully in database: $refundId -> $newStatus")

            // 6. Send notification emails and push notifications based on status change
            try {
                logger.debug("Fetching refund details to send notifications")
                val refundResult = refundRepository.getRefundById(refundId)
                if (refundResult is Result.Success && refundResult.data != null) {
                    val refund = refundResult.data

                    when (newStatus) {
                        RefundStatus.PROCESSED -> {
                            logger.debug("Sending refund processed notifications to ${refund.userEmail}")

                            // Send email notification
                            emailService.sendRefundNotification(
                                userEmail = refund.userEmail,
                                refundId = refund.refundId,
                                amount = refund.amount / 100.0,
                                paymentId = refund.paymentId,
                                status = "processed"
                            )
                            logger.info("Refund processed notification email sent to ${refund.userEmail}")

                            // Send push notification
                            sendRefundPushNotification(
                                userEmail = refund.userEmail,
                                amount = refund.amount / 100.0,
                                status = "processed"
                            )
                        }

                        RefundStatus.FAILED -> {
                            logger.debug("Sending refund failed notifications to ${refund.userEmail}")

                            // Send email notification
                            emailService.sendRefundNotification(
                                userEmail = refund.userEmail,
                                refundId = refund.refundId,
                                amount = refund.amount / 100.0,
                                paymentId = refund.paymentId,
                                status = "failed"
                            )
                            logger.info("Refund failed notification email sent to ${refund.userEmail}")

                            // Send push notification
                            sendRefundPushNotification(
                                userEmail = refund.userEmail,
                                amount = refund.amount / 100.0,
                                status = "failed"
                            )
                        }

                        RefundStatus.PENDING -> {
                            logger.debug("Refund status is PENDING, no notification sent")
                        }
                    }
                } else {
                    logger.warn("Could not fetch refund details for notifications: $refundId")
                }
            } catch (e: Exception) {
                logger.warn("Failed to send refund status notifications", e)
                // Don't fail the webhook processing if notifications fail
            }

            logger.info("Webhook processed successfully for refund: $refundId, event: $eventType, new status: $newStatus")
            Result.success(true)
        } catch (e: Exception) {
            logger.error("Unexpected error handling refund webhook", e)
            Result.error("Failed to handle webhook: ${e.message}", e)
        }
    }

    /** Verify webhook signature using HMAC SHA256. */
    private fun verifyWebhookSignature(
        receivedSignature: String,
        payload: String,
        webhookSecret: String
    ): Boolean {
        return try {
            val hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(webhookSecret.toByteArray(), "HmacSHA256")
            hmac.init(secretKey)

            val calculatedSignature =
                hmac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }

            val isValid = calculatedSignature.equals(receivedSignature, ignoreCase = true)
            if (!isValid) {
                logger.debug(
                    "Signature mismatch - Received: ${receivedSignature.take(20)}..., Calculated: ${
                        calculatedSignature.take(
                            20
                        )
                    }..."
                )
            }
            isValid
        } catch (e: Exception) {
            logger.error("Exception during webhook signature verification", e)
            false
        }
    }

    /**
     * Send push notification for refund status update.
     * Fetches user's FCM token and sends notification if available.
     */
    private suspend fun sendRefundPushNotification(
        userEmail: String,
        amount: Double,
        status: String
    ) {
        try {
            logger.debug("Attempting to send push notification to $userEmail for refund $status")

            // Get user's FCM token
            val userResult = userRepository.findUserByEmail(userEmail)
            if (userResult is Result.Success && userResult.data != null) {
                val user = userResult.data
                val fcmToken = user.fcmToken

                if (fcmToken.isNullOrBlank()) {
                    logger.debug("No FCM token found for user $userEmail, skipping push notification")
                    return
                }

                // Build notification content based on status
                val (title, body) = when (status.lowercase()) {
                    "initiated" -> {
                        Pair(
                            "Refund Initiated",
                            "Your refund of ₹${
                                String.format(
                                    "%.2f",
                                    amount
                                )
                            } is being processed. You'll be notified once completed."
                        )
                    }

                    "processed" -> {
                        Pair(
                            "Refund Completed",
                            "Your refund of ₹${String.format("%.2f", amount)} has been processed successfully."
                        )
                    }

                    "failed" -> {
                        Pair(
                            "Refund Failed",
                            "Your refund of ₹${
                                String.format(
                                    "%.2f",
                                    amount
                                )
                            } could not be processed. Please contact support."
                        )
                    }

                    else -> {
                        Pair(
                            "Refund Update",
                            "Your refund of ₹${String.format("%.2f", amount)} status: $status"
                        )
                    }
                }

                // Send push notification
                val notificationResult = notificationService.sendNotification(
                    token = fcmToken,
                    title = title,
                    body = body
                )

                if (notificationResult.isSuccess) {
                    logger.info("Push notification sent successfully to $userEmail for refund $status")
                } else {
                    logger.warn("Failed to send push notification to $userEmail: ${notificationResult.exceptionOrNull()?.message}")
                }
            } else {
                logger.warn("Could not find user $userEmail for push notification")
            }
        } catch (e: Exception) {
            logger.warn("Exception while sending push notification to $userEmail", e)
            // Don't throw - notifications are best-effort
        }
    }
}
