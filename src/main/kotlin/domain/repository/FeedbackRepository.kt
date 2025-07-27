package domain.repository

import bose.ankush.data.model.Feedback
import domain.model.Result

/**
 * Repository interface for Feedback-related operations.
 * This interface defines the contract for accessing and manipulating feedback data.
 */
interface FeedbackRepository {
    /**
     * Get feedback by ID.
     * @param id The ID of the feedback to retrieve.
     * @return Result containing the feedback if found, or an error if not found or an exception occurred.
     */
    suspend fun getFeedbackById(id: String): Result<Feedback?>

    /**
     * Create or update feedback.
     * @param feedback The feedback to create or update.
     * @return Result indicating success or failure.
     */
    suspend fun createOrUpdateFeedback(feedback: Feedback): Result<Boolean>

    /**
     * Delete feedback by ID.
     * @param id The ID of the feedback to delete.
     * @return Result indicating success or failure.
     */
    suspend fun deleteFeedbackById(id: String): Result<Boolean>

    /**
     * Get all feedback.
     * @return Result containing a list of all feedback, or an error if an exception occurred.
     */
    suspend fun getAllFeedback(): Result<List<Feedback>>
}