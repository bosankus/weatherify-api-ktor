package data.source

import bose.ankush.data.model.Feedback
import bose.ankush.data.model.User
import bose.ankush.data.model.Weather
import bose.ankush.util.getSecretValue
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import config.Environment
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bson.conversions.Bson
import org.slf4j.LoggerFactory
import util.Constants
import java.io.Closeable

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
        logger.info("Creating MongoDB client with database: $dbName")
        MongoClient.create(connectionString)
    }
    private val database: MongoDatabase by lazy { client.getDatabase(dbName) }

    init {
        // Create indexes when the module is initialized
        createIndexes()
    }

    /**
     * Create necessary indexes for collections
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
    fun createFilter(field: String, value: Any): Bson = Filters.eq(field, value)

    /**
     * Create a filter with multiple conditions
     * @param filters The filters to combine
     * @return A Bson filter that combines all the filters with AND
     */
    fun createAndFilter(vararg filters: Bson): Bson = Filters.and(*filters)

    /**
     * Create a filter with multiple conditions
     * @param filters The filters to combine
     * @return A Bson filter that combines all the filters with OR
     */
    fun createOrFilter(vararg filters: Bson): Bson = Filters.or(*filters)

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
     * Find a document by ID
     * @param collection The collection to search
     * @param id The ID of the document
     * @return The document, or null if not found
     */
    suspend fun <T : Any> findById(collection: MongoCollection<T>, id: String): T? {
        logger.debug("Finding document by ID: $id in collection: ${collection.namespace.collectionName}")
        val filter = createFilter(Constants.Database.ID_FIELD, id)
        return collection.find(filter).firstOrNull()
    }

    /**
     * Find a document by a field value
     * @param collection The collection to search
     * @param field The field name
     * @param value The field value
     * @return The document, or null if not found
     */
    suspend fun <T : Any> findByField(
        collection: MongoCollection<T>,
        field: String,
        value: Any
    ): T? {
        logger.debug("Finding document by field: $field = $value in collection: ${collection.namespace.collectionName}")
        val filter = createFilter(field, value)
        return collection.find(filter).firstOrNull()
    }

    /**
     * Find all documents in a collection
     * @param collection The collection to search
     * @return A list of all documents in the collection
     */
    suspend fun <T : Any> findAll(collection: MongoCollection<T>): List<T> {
        logger.debug("Finding all documents in collection: ${collection.namespace.collectionName}")
        return collection.find().toList()
    }

    /**
     * Update a document by ID
     * @param collection The collection to update
     * @param id The ID of the document
     * @param updates A map of field names to values
     * @return True if the update was acknowledged, false otherwise
     */
    suspend fun <T : Any> updateById(
        collection: MongoCollection<T>,
        id: String,
        updates: Map<String, Any>
    ): Boolean {
        logger.debug("Updating document by ID: $id in collection: ${collection.namespace.collectionName}")
        val filter = createFilter(Constants.Database.ID_FIELD, id)
        val update = createSetUpdates(updates)
        val result = collection.updateOne(filter, update)
        return result.wasAcknowledged()
    }

    /**
     * Replace a document by ID
     * @param collection The collection to update
     * @param id The ID of the document
     * @param replacement The replacement document
     * @param upsert Whether to insert the document if it doesn't exist
     * @return True if the replacement was acknowledged, false otherwise
     */
    suspend fun <T : Any> replaceById(
        collection: MongoCollection<T>,
        id: String,
        replacement: T,
        upsert: Boolean = false
    ): Boolean {
        logger.debug("Replacing document by ID: $id in collection: ${collection.namespace.collectionName}")
        val filter = createFilter(Constants.Database.ID_FIELD, id)
        val options = ReplaceOptions().upsert(upsert)
        val result = collection.replaceOne(filter, replacement, options)
        return result.wasAcknowledged()
    }

    /**
     * Delete a document by ID
     * @param collection The collection to delete from
     * @param id The ID of the document
     * @return True if the deletion was acknowledged, false otherwise
     */
    suspend fun <T : Any> deleteById(collection: MongoCollection<T>, id: String): Boolean {
        logger.debug("Deleting document by ID: $id in collection: ${collection.namespace.collectionName}")
        val filter = createFilter(Constants.Database.ID_FIELD, id)
        val result = collection.deleteOne(filter)
        return result.wasAcknowledged()
    }

    /**
     * Close the database connection
     */
    override fun close() {
        logger.info("Closing MongoDB client")
        client.close()
    }
}