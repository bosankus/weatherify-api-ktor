package bose.ankush.data.db

import bose.ankush.data.model.Feedback
import bose.ankush.data.model.Weather
import bose.ankush.getSecretValue
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document

object DatabaseFactory {
    private val connectionString = getSecretValue("db-connection-string")
    private val dbClient = MongoClient.create(connectionString)
    private val database = dbClient.getDatabase(getSecretValue("db-name"))
    private val feedbackCollection = database.getCollection<Feedback>("feedback")
    private val weatherCollection = database.getCollection<Weather>("weather")

    suspend fun getFeedbackById(id: String): Feedback? {
        val query = Document("_id", id)
        return feedbackCollection.find(query).firstOrNull()
    }

    suspend fun createOrUpdateFeedback(feedback: Feedback): Boolean {
        val query = Document("_id", feedback.id)
        return if (query.isEmpty()) {
            val updateResult = feedbackCollection.replaceOne(query, feedback)
            updateResult.modifiedCount > 0
        } else {
            feedbackCollection.insertOne(feedback)
            true
        }
    }

    suspend fun deleteFeedbackById(id: String): Boolean {
        val query = Document("_id", id)
        return feedbackCollection.deleteOne(query).wasAcknowledged()
    }

    suspend fun saveWeatherData(weather: Weather) {
        weatherCollection.insertOne(weather)
    }
}
