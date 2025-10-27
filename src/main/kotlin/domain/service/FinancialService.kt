package domain.service

import bose.ankush.data.model.*
import domain.model.Result

/**
 * Service interface for financial operations.
 * Handles financial metrics, payment history, and data exports.
 */
interface FinancialService {
    /**
     * Get financial metrics for admin dashboard.
     * @return Result containing financial metrics or error
     */
    suspend fun getFinancialMetrics(): Result<FinancialMetrics>

    /**
     * Get payment history with pagination and filtering.
     * @param page Page number (1-indexed)
     * @param pageSize Number of items per page
     * @param status Optional payment status filter
     * @param startDate Optional start date filter (ISO format)
     * @param endDate Optional end date filter (ISO format)
     * @return Result containing payment history response or error
     */
    suspend fun getPaymentHistory(
        page: Int,
        pageSize: Int,
        status: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ): Result<PaymentHistoryResponse>

    /**
     * Export payments to CSV format.
     * @param startDate Start date filter (ISO format)
     * @param endDate End date filter (ISO format)
     * @return Result containing CSV string or error
     */
    suspend fun exportPayments(
        startDate: String,
        endDate: String
    ): Result<String>

    /**
     * Export subscriptions to CSV format.
     * @param startDate Start date filter (ISO format)
     * @param endDate End date filter (ISO format)
     * @return Result containing CSV string or error
     */
    suspend fun exportSubscriptions(
        startDate: String,
        endDate: String
    ): Result<String>

    /**
     * Export both payments and subscriptions to CSV format.
     * @param startDate Start date filter (ISO format)
     * @param endDate End date filter (ISO format)
     * @return Result containing CSV string or error
     */
    suspend fun exportBoth(
        startDate: String,
        endDate: String
    ): Result<String>

    /**
     * Get user transactions for bill generation.
     * @param userEmail User's email address
     * @return Result containing user transactions or error
     */
    suspend fun getUserTransactions(userEmail: String): Result<UserTransactionsResponse>
}
