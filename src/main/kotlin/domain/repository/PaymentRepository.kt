package domain.repository

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
     * Get total revenue from all verified payments.
     * @return Result containing total revenue
     */
    suspend fun getTotalRevenue(): Result<Double>
}
