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
        // Create indexes for optimized queries
        kotlinx.coroutines.runBlocking {
            // === USER COLLECTION INDEXES ===

            // Unique index on email field to ensure email uniqueness
            usersCollection.createIndex(
                Indexes.ascending(Constants.Database.EMAIL_FIELD),
                IndexOptions().unique(true)
            )

            // Index on isPremium field for filtering premium users
            usersCollection.createIndex(
                Indexes.ascending("isPremium")
            )

            // === PAYMENT COLLECTION INDEXES ===

            // Index on userEmail field in payments collection for user payment lookups
            paymentsCollection.createIndex(
                Indexes.ascending("userEmail")
            )

            // Index on status field for filtering payments by status (SUCCESS, FAILED, etc.)
            // Used in payment history filtering
            paymentsCollection.createIndex(
                Indexes.ascending("status")
            )

            // Index on createdAt field for date range queries and sorting
            // Critical for financial exports and payment history pagination
            paymentsCollection.createIndex(
                Indexes.descending("createdAt")
            )

            // Compound index on status and createdAt for efficient filtered queries
            // Optimizes queries that filter by status AND sort by date
            paymentsCollection.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("status"),
                    Indexes.descending("createdAt")
                )
            )

            // Index on paymentId for quick lookups
            paymentsCollection.createIndex(
                Indexes.ascending("paymentId")
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

    /** List recent payments, most recent first */
    @Suppress("unused")
    suspend fun listRecentPayments(limit: Int = 100): List<Payment> {
        val lim = if (limit <= 0) 100 else minOf(limit, 1000)
        return paymentsCollection.find().sort(Document("createdAt", -1)).limit(lim).toList()
    }

    /** Get all verified payments (for summary aggregation) */
    @Suppress("unused")
    suspend fun getAllVerifiedPayments(): List<Payment> {
        return paymentsCollection.find(Document("status", "verified")).toList()
    }

    /** Count all payments */
    @Suppress("unused")
    suspend fun countAllPayments(): Long {
        return try {
            paymentsCollection.countDocuments()
        } catch (_: Exception) {
            0L
        }
    }

    /** Count all verified payments */
    @Suppress("unused")
    suspend fun countVerifiedPayments(): Long {
        return try {
            paymentsCollection.countDocuments(Document("status", "verified"))
        } catch (_: Exception) {
            0L
        }
    }

    /** Update a user */
    @Suppress("unused")
    suspend fun updateUser(user: User): Boolean {
        val query = createQuery("email", user.email)
        return executeDbOperation {
            usersCollection.replaceOne(
                filter = query,
                replacement = user
            )
        }
    }
}
