package domain.service

import bose.ankush.data.model.BillGenerationResponse
import domain.model.Result

/**
 * Service interface for bill generation operations.
 * Handles PDF invoice creation and email delivery.
 */
interface BillService {
    /**
     * Generate a PDF bill for a user.
     * @param userEmail User's email address
     * @param paymentIds List of payment IDs to include in the bill
     * @return Result containing PDF as byte array or error
     */
    suspend fun generateBill(
        userEmail: String,
        paymentIds: List<String>
    ): Result<ByteArray>

    /**
     * Generate a PDF bill and send it via email.
     * @param adminEmail Admin's email address (for audit logging)
     * @param userEmail User's email address
     * @param paymentIds List of payment IDs to include in the bill
     * @return Result containing bill generation response or error
     */
    suspend fun generateAndSendBill(
        adminEmail: String,
        userEmail: String,
        paymentIds: List<String>
    ): Result<BillGenerationResponse>

    /**
     * Generate a PDF bill for a single payment (original bill).
     * @param paymentId Payment transaction ID
     * @return Result containing PDF as byte array or error
     */
    suspend fun generateOriginalBill(paymentId: String): Result<ByteArray>

    /**
     * Generate a PDF bill for a single payment showing refund adjustments.
     * @param paymentId Payment transaction ID
     * @return Result containing PDF as byte array or error
     */
    suspend fun generateRefundAdjustmentBill(paymentId: String): Result<ByteArray>

    /**
     * Generate a PDF bill for a single payment showing net amount.
     * @param paymentId Payment transaction ID
     * @return Result containing PDF as byte array or error
     */
    suspend fun generateNetAmountBill(paymentId: String): Result<ByteArray>

    /**
     * Generate a PDF refund receipt for a fully refunded payment.
     * @param paymentId Payment transaction ID
     * @return Result containing PDF as byte array or error
     */
    suspend fun generateRefundReceipt(paymentId: String): Result<ByteArray>

    /**
     * Generate a PDF bill for a verified payment and email it to the user.
     * @param userEmail User's email address
     * @param paymentId Razorpay payment transaction ID
     * @return Result indicating email success or failure
     */
    suspend fun sendPaymentBillEmail(
        userEmail: String,
        paymentId: String
    ): Result<Boolean>
}
