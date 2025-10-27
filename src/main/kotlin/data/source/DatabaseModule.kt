package data.source

import bose.ankush.data.model.Feedback
import bose.ankush.data.model.User
import bose.ankush.data.model.Weather
import bose.ankush.util.getSecretValue
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import config.Environment
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bson.conversions.Bson
import org.slf4j.LoggerFactory
import util.Constants
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Database module that provides MongoDB connections and collections.
 * This class follows the Dependency Injection principle by allowing its dependencies to be injected.
 * It implements Closeable to ensure proper resource cleanup.
 */
class DatabaseModule(
    private val connectionStringSecretName: String = Constants.Auth.DB_CONNECTION_STRING_SECRET,
    private val dbName: String = Environment.getDbName()
) : Closeable {
    private val logger = LoggerFactory.getLogger(DatabaseModule::class.java)
    private val connectionString: String by lazy { getSecretValue(connectionStringSecretName) }
    private val client: MongoClient by lazy {
        logger.info("Creating MongoDB client with database: $dbName and optimized connection pool")
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            .applyToConnectionPoolSettings { builder ->
                builder
                    .maxSize(100)
                    .minSize(10)
                    .maxWaitTime(2, TimeUnit.SECONDS)
                    .maxConnectionIdleTime(30, TimeUnit.SECONDS)
            }
            .applyToSocketSettings { builder ->
                builder
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
            }
            .build()

        MongoClient.create(settings)
    }
    private val database: MongoDatabase by lazy { client.getDatabase(dbName) }

    init {
        // Create indexes when the module is initialized
        createIndexes()
    }

    /**
     * Create the necessary indexes for collections
     */
    private fun createIndexes() {
        runBlocking {
            try {
                logger.info("Creating indexes for collections")
                // Create a unique index on the email field in users collection
                getUsersCollection().createIndex(
                    Indexes.ascending(Constants.Database.EMAIL_FIELD),
                    IndexOptions().unique(true)
                )

                // Create indexes for refunds collection
                val refundsCollection = getRefundsCollection()

                // Unique index on refundId
                refundsCollection.createIndex(
                    Indexes.ascending("refundId"),
                    IndexOptions().unique(true)
                )

                // Index on paymentId for querying refunds by payment
                refundsCollection.createIndex(
                    Indexes.ascending("paymentId")
                )

                // Index on userEmail for querying refunds by user
                refundsCollection.createIndex(
                    Indexes.ascending("userEmail")
                )

                // Index on status for filtering by refund status
                refundsCollection.createIndex(
                    Indexes.ascending("status")
                )

                // Index on createdAt for sorting and date range queries (descending for recent first)
                refundsCollection.createIndex(
                    Indexes.descending("createdAt")
                )

                // Index on processedBy for tracking admin actions
                refundsCollection.createIndex(
                    Indexes.ascending("processedBy")
                )

                // Compound index on status + createdAt for filtered queries with sorting
                refundsCollection.createIndex(
                    Indexes.compoundIndex(
                        Indexes.ascending("status"),
                        Indexes.descending("createdAt")
                    )
                )

                // Compound index on paymentId + status for refund summary queries
                refundsCollection.createIndex(
                    Indexes.compoundIndex(
                        Indexes.ascending("paymentId"),
                        Indexes.ascending("status")
                    )
                )

                // Create indexes for payments collection
                val paymentsCollection = getPaymentsCollection()

                // Index on razorpay_payment_id for transaction lookups
                paymentsCollection.createIndex(
                    Indexes.ascending("razorpay_payment_id")
                )

                // Compound index on userEmail + status for user payment queries
                paymentsCollection.createIndex(
                    Indexes.compoundIndex(
                        Indexes.ascending("userEmail"),
                        Indexes.ascending("status")
                    )
                )

                // Compound index on serviceType + status for revenue calculations
                paymentsCollection.createIndex(
                    Indexes.compoundIndex(
                        Indexes.ascending("serviceType"),
                        Indexes.ascending("status")
                    )
                )

                // Index on createdAt for sorting
                paymentsCollection.createIndex(
                    Indexes.descending("createdAt")
                )

                logger.info("Indexes created successfully")
            } catch (e: Exception) {
                logger.error("Failed to create indexes", e)
            }
        }
    }

    /**
     * Get a collection with the specified name and type
     * @param collectionName The name of the collection
     * @return The MongoDB collection
     * @throws IllegalStateException if the collection cannot be accessed
     */
    private inline fun <reified T : Any> getCollection(collectionName: String): MongoCollection<T> {
        logger.debug("Getting collection: $collectionName")
        try {
            val collection = database.getCollection<T>(collectionName)
            return collection
        } catch (e: Exception) {
            logger.error("Failed to get collection: $collectionName", e)
            throw IllegalStateException(
                "Failed to access collection $collectionName: ${e.message}",
                e
            )
        }
    }

    /**
     * Get the users collection
     * @return The users collection
     * @throws IllegalStateException if the collection cannot be accessed
     */
    fun getUsersCollection(): MongoCollection<User> {
        try {
            logger.debug("Getting users collection")
            return getCollection<User>(Constants.Database.USERS_COLLECTION)
        } catch (e: Exception) {
            logger.error("Failed to get users collection", e)
            throw IllegalStateException("Failed to access users collection: ${e.message}", e)
        }
    }

    /**
     * Get the feedback collection
     * @return The feedback collection
     */
    fun getFeedbackCollection(): MongoCollection<Feedback> {
        return getCollection<Feedback>(Constants.Database.FEEDBACK_COLLECTION)
    }

    /**
     * Get the weather collection
     * @return The weather collection
     */
    fun getWeatherCollection(): MongoCollection<Weather> {
        return getCollection<Weather>(Constants.Database.WEATHER_COLLECTION)
    }

    /**
     * Get the payment collection
     * @return The payment collection
     */
    fun getPaymentsCollection(): MongoCollection<bose.ankush.data.model.Payment> {
        return getCollection<bose.ankush.data.model.Payment>(Constants.Database.PAYMENTS_COLLECTION)
    }

    /**
     * Get the refunds collection
     * @return The refunds collection
     */
    fun getRefundsCollection(): MongoCollection<bose.ankush.data.model.Refund> {
        return getCollection<bose.ankush.data.model.Refund>(Constants.Database.REFUNDS_COLLECTION)
    }

    /**
     * Get the services collection
     * @return The services collection
     */
    fun getServicesCollection(): MongoCollection<bose.ankush.data.model.ServiceConfig> {
        return getCollection<bose.ankush.data.model.ServiceConfig>("services")
    }

    /**
     * Get the service history collection
     * @return The service history collection
     */
    fun getServiceHistoryCollection(): MongoCollection<bose.ankush.data.model.ServiceHistory> {
        return getCollection<bose.ankush.data.model.ServiceHistory>("service_history")
    }

    /**
     * Create a query document with a single field
     * @param field The field name
     * @param value The field value
     * @return A Document with the field and value
     */
    fun createQuery(field: String, value: Any): Document = Document(field, value)

    /**
     * Create a query document with multiple fields
     * @param fields A map of field names to values
     * @return A Document with the fields and values
     */
    fun createQuery(fields: Map<String, Any>): Document {
        val document = Document()
        fields.forEach { (field, value) -> document.append(field, value) }
        return document
    }

    /**
     * Create a filter for a field with a specific value
     * @param field The field name
     * @param value The field value
     * @return A Bson filter
     */
    fun createFilter(field: String, value: Any): Bson {
        return if (field == Constants.Database.ID_FIELD) {
            when (value) {
                is String -> {
                    // Support both ObjectId-based and legacy string-based _id
                    if (org.bson.types.ObjectId.isValid(value)) {
                        Filters.or(
                            Filters.eq(field, org.bson.types.ObjectId(value)),
                            Filters.eq(field, value)
                        )
                    } else {
                        Filters.eq(field, value)
                    }
                }

                is org.bson.types.ObjectId -> Filters.eq(field, value)
                else -> Filters.eq(field, value)
            }
        } else {
            Filters.eq(field, value)
        }
    }


    /**
     * Create an update operation to set a field to a value
     * @param field The field name
     * @param value The field value
     * @return A Bson update operation
     */
    fun createSetUpdate(field: String, value: Any): Bson = Updates.set(field, value)

    /**
     * Create an update operation with multiple set operations
     * @param updates A map of field names to values
     * @return A Bson update operation that sets all the fields to their values
     */
    fun createSetUpdates(updates: Map<String, Any>): Bson {
        val updatesList = updates.map { (field, value) -> Updates.set(field, value) }
        return Updates.combine(updatesList)
    }







    /**
     * Close the database connection
     */
    override fun close() {
        logger.info("Closing MongoDB client")
        client.close()
    }
}
