package domain.repository

import bose.ankush.data.model.MonthlyRevenue
import bose.ankush.data.model.Payment
import domain.model.Result

/**
 * Repository interface for Payment-related operations.
 * This interface defines read-only operations for accessing payment data.
 */
interface PaymentRepository {
    /**
     * Get a payment by transaction ID.
     * @param transactionId The payment transaction ID
     * @return Result containing the payment if found, or null if not found
     */
    suspend fun getPaymentByTransactionId(transactionId: String): Result<Payment?>

    /**
     * Get all payments for a user by user ID.
     * @param userId The user ID
     * @return Result containing list of payments
     */
    suspend fun getPaymentsByUserId(userId: String): Result<List<Payment>>

    /**
     * Get all payments for a user by email.
     * @param userEmail The user email
     * @return Result containing list of payments
     */
    suspend fun getPaymentsByUserEmail(userEmail: String): Result<List<Payment>>

    /**
     * Get all payments with pagination.
     * @param page Page number (1-indexed)
     * @param pageSize Number of items per page
     * @return Result containing pair of (payments list, total count)
     */
    suspend fun getAllPayments(page: Int, pageSize: Int): Result<Pair<List<Payment>, Long>>

    /**
     * Get all payments with pagination, status filter, and date range filter.
     * Uses database-level filtering for better performance.
     * @param page Page number (1-indexed)
     * @param pageSize Number of items per page
     * @param status Optional status filter
     * @param startDate Optional start date filter (ISO format)
     * @param endDate Optional end date filter (ISO format)
     * @return Result containing pair of (payments list, total count)
     */
    suspend fun getPaymentsWithFilters(
        page: Int,
        pageSize: Int,
        status: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ): Result<Pair<List<Payment>, Long>>

    /**
     * Get total revenue from all verified payments.
     * @return Result containing total revenue
     */
    suspend fun getTotalRevenue(): Result<Double>

    /**
     * Get verified payments aggregate (total amount and count) using database aggregation.
     * This is much more efficient than fetching all records and filtering in memory.
     * @return Result containing pair of (totalAmount in paise, count)
     */
    suspend fun getVerifiedPaymentsAggregate(): Result<Pair<Long, Long>>

    /**
     * Get monthly revenue for a date range using database aggregation.
     * @param startDate Start date (ISO format)
     * @param endDate End date (ISO format)
     * @return Result containing list of monthly revenue data
     */
    suspend fun getMonthlyRevenue(startDate: String, endDate: String): Result<List<MonthlyRevenue>>

    /**
     * Get payment count by status using database aggregation.
     * @return Result containing map of status to count
     */
    suspend fun getPaymentCountByStatus(): Result<Map<String, Long>>

    /**
     * Get verified payments count.
     * @return Result containing count of verified payments
     */
    suspend fun getVerifiedPaymentsCount(): Result<Long>

    /**
     * Get successful payment counts grouped by service code using database aggregation.
     * Only counts payments with status "verified", "captured", or "success".
     * @return Result containing map of serviceCode to count
     */
    suspend fun getPaymentCountByServiceCode(): Result<Map<String, Long>>

    /**
     * Get analytics (count + revenue) for a specific service code using database aggregation.
     * Only counts payments with status "verified", "captured", or "success".
     * @param serviceCode The service code to filter by
     * @return Result containing Triple of (totalCount, totalRevenuePaise, monthlyData map of "yyyy-MM" to Pair(count, revenuePaise))
     */
    suspend fun getServiceAnalyticsAggregate(serviceCode: String): Result<Triple<Long, Long, Map<String, Pair<Long, Long>>>>
}
