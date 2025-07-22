package bose.ankush.data.db

import bose.ankush.config.Environment
import bose.ankush.data.model.Feedback
import bose.ankush.data.model.Weather
import bose.ankush.getSecretValue
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document

object DatabaseFactory {
    private val connectionString = getSecretValue("db-connection-string")
    private val dbClient = MongoClient.create(connectionString)
    private val database = dbClient.getDatabase(Environment.getDbName())
    private val feedbackCollection = database.getCollection<Feedback>("feedback")
    private val weatherCollection = database.getCollection<Weather>("weather")

    suspend fun getFeedbackById(id: String): Feedback? {
        val query = Document("_id", id)
        return feedbackCollection.find(query).firstOrNull()
    }

    suspend fun createOrUpdateFeedback(feedback: Feedback): Boolean {
        val query = Document("_id", feedback.id)
        return try {
            // Use upsert operation to reduce database round trips
            val updateResult = feedbackCollection.replaceOne(
                filter = query,
                replacement = feedback,
                options = com.mongodb.client.model.ReplaceOptions().upsert(true)
            )
            updateResult.wasAcknowledged()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteFeedbackById(id: String): Boolean {
        val query = Document("_id", id)
        return feedbackCollection.deleteOne(query).wasAcknowledged()
    }

    suspend fun saveWeatherData(weather: Weather): Boolean {
        return try {
            weatherCollection.insertOne(weather)
            true
        } catch (_: Exception) {
            false
        }
    }
}
