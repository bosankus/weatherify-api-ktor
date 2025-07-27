package data.source

import bose.ankush.data.model.Feedback
import bose.ankush.data.model.User
import bose.ankush.data.model.Weather
import bose.ankush.util.getSecretValue
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import config.Environment
import kotlinx.coroutines.runBlocking
import org.bson.Document
import util.Constants

/**
 * Database module that provides MongoDB connections and collections.
 * This class follows the Dependency Injection principle by allowing its dependencies to be injected.
 */
class DatabaseModule(
    private val connectionStringSecretName: String = Constants.Auth.DB_CONNECTION_STRING_SECRET,
    private val dbName: String = Environment.getDbName()
) {
    private val connectionString: String by lazy { getSecretValue(connectionStringSecretName) }
    private val client: MongoClient by lazy { MongoClient.create(connectionString) }
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
            // Create a unique index on the email field in users collection
            getUsersCollection().createIndex(
                Indexes.ascending(Constants.Database.EMAIL_FIELD),
                IndexOptions().unique(true)
            )
        }
    }

    /**
     * Get the users collection (private implementation)
     */
    private inline fun <reified T : Any> getCollection(collectionName: String): MongoCollection<T> {
        return database.getCollection<T>(collectionName)
    }

    /**
     * Get the users collection
     */
    fun getUsersCollection(): MongoCollection<User> {
        return getCollection<User>(Constants.Database.USERS_COLLECTION)
    }

    /**
     * Get the feedback collection
     */
    fun getFeedbackCollection(): MongoCollection<Feedback> {
        return getCollection<Feedback>(Constants.Database.FEEDBACK_COLLECTION)
    }

    /**
     * Get the weather collection
     */
    fun getWeatherCollection(): MongoCollection<Weather> {
        return getCollection<Weather>(Constants.Database.WEATHER_COLLECTION)
    }

    /**
     * Create a query document with a single field
     */
    fun createQuery(field: String, value: Any): Document = Document(field, value)

    /**
     * Close the database connection
     */
    /*fun close() {
        client.close()
    }*/
}