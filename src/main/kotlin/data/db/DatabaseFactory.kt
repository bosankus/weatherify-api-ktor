package bose.ankush.data.db

import bose.ankush.data.model.Feedback
import bose.ankush.data.model.Payment
import bose.ankush.data.model.User
import bose.ankush.data.model.Weather
import bose.ankush.util.getSecretValue
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoClient
import config.Environment
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import util.Constants

/** Factory for database operations */
object DatabaseFactory {
    private val connectionString = getSecretValue(Constants.Auth.DB_CONNECTION_STRING_SECRET)
    private val dbClient = MongoClient.create(connectionString)
    private val database = dbClient.getDatabase(Environment.getDbName())
    private val feedbackCollection =
        database.getCollection<Feedback>(Constants.Database.FEEDBACK_COLLECTION)
    private val weatherCollection =
        database.getCollection<Weather>(Constants.Database.WEATHER_COLLECTION)
    private val usersCollection = database.getCollection<User>(Constants.Database.USERS_COLLECTION)
    private val paymentsCollection =
        database.getCollection<Payment>(Constants.Database.PAYMENTS_COLLECTION)

    init {
        // Create a unique index on the email field to ensure email uniqueness
        kotlinx.coroutines.runBlocking {
            usersCollection.createIndex(
                Indexes.ascending(Constants.Database.EMAIL_FIELD),
                IndexOptions().unique(true)
            )
        }
    }

    /** Create a query document with a single field */
    private fun createQuery(field: String, value: Any): Document = Document(field, value)

    /** Execute a database operation safely, returning false on exception */
    private suspend fun <T> executeDbOperation(operation: suspend () -> T): Boolean {
        return try {
            operation()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getFeedbackById(id: String): Feedback? {
        val query = createQuery(Constants.Database.ID_FIELD, id)
        return feedbackCollection.find(query).firstOrNull()
    }

    suspend fun createOrUpdateFeedback(feedback: Feedback): Boolean {
        val query = createQuery(Constants.Database.ID_FIELD, feedback.id)
        return executeDbOperation {
            // Use upsert operation to reduce database round trips
            feedbackCollection.replaceOne(
                filter = query,
                replacement = feedback,
                options = ReplaceOptions().upsert(true)
            )
        }
    }

    suspend fun deleteFeedbackById(id: String): Boolean {
        val query = createQuery(Constants.Database.ID_FIELD, id)
        return feedbackCollection.deleteOne(query).wasAcknowledged()
    }

    suspend fun saveWeatherData(weather: Weather): Boolean {
        return executeDbOperation {
            weatherCollection.insertOne(weather)
        }
    }

    /** Find a user by email */
    @Suppress("unused")
    suspend fun findUserByEmail(email: String): User? {
        val query = createQuery(Constants.Database.EMAIL_FIELD, email)
        return usersCollection.find(query).firstOrNull()
    }

    /** Create a new user */
    @Suppress("unused")
    suspend fun createUser(user: User): Boolean {
        return executeDbOperation {
            usersCollection.insertOne(user)
        }
    }

    /** Save a verified payment */
    suspend fun savePayment(payment: Payment): Boolean {
        return executeDbOperation {
            paymentsCollection.insertOne(payment)
        }
    }

    /** Get payments by user email */
    @Suppress("unused")
    suspend fun getPaymentsByEmail(email: String): List<Payment> {
        val query = createQuery(Constants.Database.EMAIL_FIELD, email).let {
            // our Payment uses userEmail field, keep compatibility by mapping
            Document("userEmail", email)
        }
        return paymentsCollection.find(query).toList()
    }

    /** Update a user */
    /*suspend fun updateUser(user: User): Boolean {
        val query = createQuery("email", user.email)
        return executeDbOperation {
            usersCollection.replaceOne(
                filter = query,
                replacement = user
            )
        }
    }*/
}
