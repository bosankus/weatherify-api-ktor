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
     * @param subscriptionIds List of subscription IDs to include in the bill
     * @return Result containing PDF as byte array or error
     */
    suspend fun generateBill(
        userEmail: String,
        paymentIds: List<String>,
        subscriptionIds: List<String>
    ): Result<ByteArray>

    /**
     * Generate a PDF bill and send it via email.
     * @param adminEmail Admin's email address (for audit logging)
     * @param userEmail User's email address
     * @param paymentIds List of payment IDs to include in the bill
     * @param subscriptionIds List of subscription IDs to include in the bill
     * @return Result containing bill generation response or error
     */
    suspend fun generateAndSendBill(
        adminEmail: String,
        userEmail: String,
        paymentIds: List<String>,
        subscriptionIds: List<String>
    ): Result<BillGenerationResponse>
}
