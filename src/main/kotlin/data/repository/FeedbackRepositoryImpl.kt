package data.repository

import bose.ankush.data.model.Feedback
import com.mongodb.client.model.ReplaceOptions
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.FeedbackRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import util.Constants

/**
 * Implementation of FeedbackRepository that uses MongoDB for data storage.
 */
class FeedbackRepositoryImpl(private val databaseModule: DatabaseModule) : FeedbackRepository {

    /**
     * Get feedback by ID.
     * @param id The ID of the feedback to retrieve.
     * @return Result containing the feedback if found, or an error if not found or an exception occurred.
     */
    override suspend fun getFeedbackById(id: String): Result<Feedback?> {
        return try {
            val query = databaseModule.createQuery(Constants.Database.ID_FIELD, id)
            val feedback = databaseModule.getFeedbackCollection().find(query).firstOrNull()
            Result.success(feedback)
        } catch (e: Exception) {
            Result.error("Failed to get feedback by ID: ${e.message}", e)
        }
    }

    /**
     * Create or update feedback.
     * @param feedback The feedback to create or update.
     * @return Result indicating success or failure.
     */
    override suspend fun createOrUpdateFeedback(feedback: Feedback): Result<Boolean> {
        return try {
            val query = databaseModule.createQuery(Constants.Database.ID_FIELD, feedback.id)
            val result = databaseModule.getFeedbackCollection().replaceOne(
                filter = query,
                replacement = feedback,
                options = ReplaceOptions().upsert(true)
            )
            Result.success(result.wasAcknowledged())
        } catch (e: Exception) {
            Result.error("Failed to create or update feedback: ${e.message}", e)
        }
    }

    /**
     * Delete feedback by ID.
     * @param id The ID of the feedback to delete.
     * @return Result indicating success or failure.
     */
    override suspend fun deleteFeedbackById(id: String): Result<Boolean> {
        return try {
            val query = databaseModule.createQuery(Constants.Database.ID_FIELD, id)
            val result = databaseModule.getFeedbackCollection().deleteOne(query)
            Result.success(result.wasAcknowledged())
        } catch (e: Exception) {
            Result.error("Failed to delete feedback: ${e.message}", e)
        }
    }

    /**
     * Get all feedback.
     * @return Result containing a list of all feedback, or an error if an exception occurred.
     */
    override suspend fun getAllFeedback(): Result<List<Feedback>> {
        return try {
            val feedbackList = databaseModule.getFeedbackCollection().find().toList()
            Result.success(feedbackList)
        } catch (e: Exception) {
            Result.error("Failed to get all feedback: ${e.message}", e)
        }
    }
}