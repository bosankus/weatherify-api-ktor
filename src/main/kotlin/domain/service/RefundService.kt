package domain.service

import bose.ankush.data.model.*
import domain.model.Result

/**
 * Service interface for refund operations.
 * Handles refund initiation, tracking, analytics, and webhook processing.
 */
interface RefundService {
    /**
     * Initiate a refund through Razorpay API.
     * @param adminEmail Email of the admin initiating the refund
     * @param request Refund request details
     * @return Result containing refund response
     */
    suspend fun initiateRefund(
        adminEmail: String,
        request: InitiateRefundRequest
    ): Result<RefundResponse>

    /**
     * Get refund details by refund ID.
     * @param refundId The Razorpay refund ID
     * @return Result containing refund DTO
     */
    suspend fun getRefund(refundId: String): Result<RefundDto>

    /**
     * Get all refunds for a payment with summary.
     * @param paymentId The payment ID
     * @return Result containing payment refund summary
     */
    suspend fun getRefundsForPayment(paymentId: String): Result<PaymentRefundSummary>

    /**
     * Check refund status from Razorpay API and sync with local database.
     * @param paymentId The payment ID
     * @return Result containing payment refund summary with latest data from Razorpay
     */
    suspend fun checkPaymentRefundStatus(paymentId: String): Result<PaymentRefundSummary>

    /**
     * Get refund history with pagination and filtering.
     * @param page Page number (1-indexed)
     * @param pageSize Number of items per page
     * @param status Optional status filter
     * @param startDate Optional start date filter (ISO format)
     * @param endDate Optional end date filter (ISO format)
     * @return Result containing refund history response
     */
    suspend fun getRefundHistory(
        page: Int,
        pageSize: Int,
        status: RefundStatus? = null,
        startDate: String? = null,
        endDate: String? = null
    ): Result<RefundHistoryResponse>

    /**
     * Get refund metrics for dashboard analytics.
     * @return Result containing refund metrics
     */
    suspend fun getRefundMetrics(): Result<RefundMetrics>

    /**
     * Export refunds to CSV format.
     * @param startDate Start date for export (ISO format)
     * @param endDate End date for export (ISO format)
     * @return Result containing CSV string
     */
    suspend fun exportRefunds(
        startDate: String,
        endDate: String
    ): Result<String>

    /**
     * Handle webhook notification from Razorpay.
     * @param signature Webhook signature from X-Razorpay-Signature header
     * @param payload Raw webhook payload
     * @return Result indicating success or failure
     */
    suspend fun handleRefundWebhook(
        signature: String,
        payload: String
    ): Result<Boolean>
}
