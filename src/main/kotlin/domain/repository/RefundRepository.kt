package domain.repository

import bose.ankush.data.model.MonthlyRefundData
import bose.ankush.data.model.Refund
import bose.ankush.data.model.RefundStatus
import domain.model.Result

/**
 * Repository interface for Refund-related operations.
 * This interface defines operations for managing refund data.
 */
interface RefundRepository {
    /**
     * Create a new refund record.
     * @param refund The refund to create
     * @return Result containing true if successful
     */
    suspend fun createRefund(refund: Refund): Result<Boolean>

    /**
     * Get a refund by refund ID.
     * @param refundId The Razorpay refund ID
     * @return Result containing the refund if found, or null if not found
     */
    suspend fun getRefundById(refundId: String): Result<Refund?>

    /**
     * Get all refunds for a payment.
     * @param paymentId The payment ID
     * @return Result containing list of refunds
     */
    suspend fun getRefundsByPaymentId(paymentId: String): Result<List<Refund>>

    /**
     * Get all refunds for a user by email.
     * @param userEmail The user email
     * @return Result containing list of refunds
     */
    suspend fun getRefundsByUserEmail(userEmail: String): Result<List<Refund>>

    /**
     * Get all refunds with pagination and filtering.
     * @param page Page number (1-indexed)
     * @param pageSize Number of items per page
     * @param status Optional status filter
     * @param startDate Optional start date filter (ISO format)
     * @param endDate Optional end date filter (ISO format)
     * @return Result containing pair of (refunds list, total count)
     */
    suspend fun getAllRefunds(
        page: Int,
        pageSize: Int,
        status: RefundStatus? = null,
        startDate: String? = null,
        endDate: String? = null
    ): Result<Pair<List<Refund>, Long>>

    /**
     * Update refund status.
     * @param refundId The refund ID to update
     * @param status The new status
     * @param processedAt Optional processed timestamp
     * @param errorCode Optional error code
     * @param errorDescription Optional error description
     * @return Result containing true if successful
     */
    suspend fun updateRefundStatus(
        refundId: String,
        status: RefundStatus,
        processedAt: String? = null,
        errorCode: String? = null,
        errorDescription: String? = null
    ): Result<Boolean>

    /**
     * Get total refunded amount across all processed refunds.
     * @return Result containing total refunded amount in paise
     */
    suspend fun getTotalRefundedAmount(): Result<Double>

    /**
     * Get monthly refunded amount for a specific month.
     * @param month Month in format "YYYY-MM"
     * @return Result containing refunded amount in paise for the month
     */
    suspend fun getMonthlyRefundedAmount(month: String): Result<Double>

    /**
     * Get refund count by speed (instant vs normal).
     * @return Result containing pair of (instant count, normal count)
     */
    suspend fun getRefundCountBySpeed(): Result<Pair<Int, Int>>

    /**
     * Get average processing time for completed refunds.
     * @return Result containing average processing time in hours
     */
    suspend fun getAverageProcessingTime(): Result<Double>

    /**
     * Get monthly refund data for the last N months.
     * @param months Number of months to retrieve
     * @return Result containing list of monthly refund data
     */
    suspend fun getMonthlyRefundData(months: Int): Result<List<MonthlyRefundData>>

    /**
     * Get total refunded amount for a specific payment.
     * @param paymentId The payment ID
     * @return Result containing total refunded amount in paise
     */
    suspend fun getTotalRefundedForPayment(paymentId: String): Result<Int>
}
