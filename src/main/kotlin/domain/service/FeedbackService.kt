package domain.service

import bose.ankush.data.model.Feedback
import domain.model.Result

/**
 * Service interface for feedback-related operations.
 * This interface defines the contract for feedback business logic.
 */
interface FeedbackService {
    /**
     * Get feedback by ID.
     * @param id The ID of the feedback to retrieve.
     * @return Result containing the feedback if found, or an error if not found or an exception occurred.
     */
    suspend fun getFeedbackById(id: String): Result<Feedback>

    /**
     * Submit new feedback.
     * @param deviceId The ID of the device submitting the feedback.
     * @param deviceOs The operating system of the device.
     * @param title The title of the feedback.
     * @param description The description of the feedback.
     * @return Result containing the ID of the created feedback if successful, or an error if submission failed.
     */
    suspend fun submitFeedback(
        deviceId: String,
        deviceOs: String,
        title: String,
        description: String
    ): Result<String>

    /**
     * Delete feedback by ID.
     * @param id The ID of the feedback to delete.
     * @return Result indicating success or failure.
     */
    suspend fun deleteFeedback(id: String): Result<Unit>

    /**
     * Get all feedback.
     * @return Result containing a list of all feedback, or an error if an exception occurred.
     */
    suspend fun getAllFeedback(): Result<List<Feedback>>

    /**
     * Validate feedback parameters.
     * @param deviceId The ID of the device.
     * @param deviceOs The operating system of the device.
     * @param title The title of the feedback.
     * @param description The description of the feedback.
     * @return Result indicating if the parameters are valid, or an error with validation message.
     */
    fun validateFeedbackParams(
        deviceId: String,
        deviceOs: String,
        title: String,
        description: String
    ): Result<Unit>
}