package domain.service

import domain.model.Result

/**
 * Service for sending email notifications to users
 */
interface EmailService {
    /**
     * Send a subscription cancellation email to the user
     * @param userEmail The user's email address
     * @param subscriptionEndDate The end date of the cancelled subscription
     * @return Result indicating success or failure
     */
    suspend fun sendCancellationEmail(
        userEmail: String,
        subscriptionEndDate: String
    ): Result<Boolean>

    /**
     * Send a refund notification email to the user
     * @param userEmail The user's email address
     * @param refundId The refund ID
     * @param amount The refund amount in rupees
     * @param paymentId The original payment ID
     * @param status The refund status (initiated or processed)
     * @return Result indicating success or failure
     */
    suspend fun sendRefundNotification(
        userEmail: String,
        refundId: String,
        amount: Double,
        paymentId: String,
        status: String
    ): Result<Boolean>

    /**
     * Send an email with PDF attachment
     * @param userEmail The recipient's email address
     * @param subject The email subject
     * @param htmlContent The HTML content of the email
     * @param attachmentData The PDF file data as byte array
     * @param attachmentFilename The filename for the attachment
     * @return Result indicating success or failure
     */
    suspend fun sendEmailWithAttachment(
        userEmail: String,
        subject: String,
        htmlContent: String,
        attachmentData: ByteArray,
        attachmentFilename: String
    ): Result<Boolean>
}
