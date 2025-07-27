package domain.service.impl

import bose.ankush.data.model.Feedback
import domain.model.Result
import domain.repository.FeedbackRepository
import domain.service.FeedbackService
import util.Constants

/**
 * Implementation of FeedbackService that handles feedback business logic.
 */
class FeedbackServiceImpl(private val feedbackRepository: FeedbackRepository) : FeedbackService {

    /**
     * Get feedback by ID.
     * @param id The ID of the feedback to retrieve.
     * @return Result containing the feedback if found, or an error if not found or an exception occurred.
     */
    override suspend fun getFeedbackById(id: String): Result<Feedback> {
        val result = feedbackRepository.getFeedbackById(id)

        return when {
            result.isError -> Result.error(
                result.getOrNull()?.toString() ?: Constants.Messages.FEEDBACK_NOT_FOUND
            )

            result.getOrNull() == null -> Result.error(Constants.Messages.FEEDBACK_NOT_FOUND)
            else -> Result.success(result.getOrNull()!!)
        }
    }

    /**
     * Submit new feedback.
     * @param deviceId The ID of the device submitting the feedback.
     * @param deviceOs The operating system of the device.
     * @param title The title of the feedback.
     * @param description The description of the feedback.
     * @return Result containing the ID of the created feedback if successful, or an error if submission failed.
     */
    override suspend fun submitFeedback(
        deviceId: String,
        deviceOs: String,
        title: String,
        description: String
    ): Result<String> {
        // Validate feedback parameters
        val validationResult = validateFeedbackParams(deviceId, deviceOs, title, description)
        if (validationResult.isError) {
            return Result.error(
                validationResult.getOrNull()?.toString() ?: "Invalid feedback parameters"
            )
        }

        // Create feedback object
        val feedback = Feedback(
            deviceId = deviceId,
            deviceOs = deviceOs,
            feedbackTitle = title,
            feedbackDescription = description
        )

        // Save feedback to database
        val saveResult = feedbackRepository.createOrUpdateFeedback(feedback)

        return if (saveResult.isSuccess && saveResult.getOrNull() == true) {
            Result.success(feedback.id)
        } else {
            Result.error(Constants.Messages.FAILED_SAVE_FEEDBACK)
        }
    }

    /**
     * Delete feedback by ID.
     * @param id The ID of the feedback to delete.
     * @return Result indicating success or failure.
     */
    override suspend fun deleteFeedback(id: String): Result<Unit> {
        val result = feedbackRepository.deleteFeedbackById(id)

        return if (result.isSuccess && result.getOrNull() == true) {
            Result.success(Unit)
        } else {
            Result.error(Constants.Messages.FEEDBACK_REMOVAL_FAILED)
        }
    }

    /**
     * Get all feedback.
     * @return Result containing a list of all feedback, or an error if an exception occurred.
     */
    override suspend fun getAllFeedback(): Result<List<Feedback>> {
        return feedbackRepository.getAllFeedback()
    }

    /**
     * Validate feedback parameters.
     * @param deviceId The ID of the device.
     * @param deviceOs The operating system of the device.
     * @param title The title of the feedback.
     * @param description The description of the feedback.
     * @return Result indicating if the parameters are valid, or an error with validation message.
     */
    override fun validateFeedbackParams(
        deviceId: String,
        deviceOs: String,
        title: String,
        description: String
    ): Result<Unit> {
        when {
            deviceId.isBlank() -> return Result.error("Device ID cannot be empty")
            deviceOs.isBlank() -> return Result.error("Device OS cannot be empty")
            title.isBlank() -> return Result.error("Feedback title cannot be empty")
            description.isBlank() -> return Result.error("Feedback description cannot be empty")
            title.length > 100 -> return Result.error("Feedback title cannot exceed 100 characters")
            description.length > 1000 -> return Result.error("Feedback description cannot exceed 1000 characters")
        }

        return Result.success(Unit)
    }
}