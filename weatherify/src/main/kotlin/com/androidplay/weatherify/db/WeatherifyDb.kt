package com.androidplay.weatherify.db

import com.androidplay.weatherify.domain.*
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory

/**
 * Provides typed MongoDB collections and BSON helper utilities.
 * The [MongoDatabase] connection is created externally (via [com.transloom.core.mongodb.MongoDatabaseFactory])
 * and injected here — this class owns no MongoClient and has no lifecycle responsibility.
 */
class WeatherifyDb(private val database: MongoDatabase) {
    private val logger = LoggerFactory.getLogger(WeatherifyDb::class.java)

    suspend fun checkHealth(): Boolean = try {
        database.runCommand(Document("ping", 1))
        true
    } catch (e: Exception) {
        logger.error("Database health check failed", e)
        false
    }

    private inline fun <reified T : Any> getCollection(collectionName: String): MongoCollection<T> {
        return try {
            database.getCollection<T>(collectionName)
        } catch (e: Exception) {
            logger.error("Failed to get collection: $collectionName", e)
            throw IllegalStateException("Failed to access collection $collectionName: ${e.message}", e)
        }
    }

    fun getUsersCollection(): MongoCollection<User> = getCollection("users")
    fun getPaymentsRawCollection(): MongoCollection<Document> = getCollection("payments")
    fun getFeedbackCollection(): MongoCollection<Feedback> = getCollection("feedback")
    fun getSavedLocationsCollection(): MongoCollection<SavedLocation> = getCollection("saved_locations")
    fun getPaymentsCollection(): MongoCollection<Payment> = getCollection("payments")
    fun getRefundsCollection(): MongoCollection<Refund> = getCollection("refunds")
    fun getServicesCollection(): MongoCollection<ServiceConfig> = getCollection("services")
    fun getServiceHistoryCollection(): MongoCollection<ServiceHistory> = getCollection("service_history")
    fun getNotesCollection(): MongoCollection<Note> = getCollection("notes")

    fun createQuery(field: String, value: Any): Document = Document(field, value)

    fun createQuery(fields: Map<String, Any>): Document {
        val document = Document()
        fields.forEach { (field, value) -> document.append(field, value) }
        return document
    }

    fun createFilter(field: String, value: Any): Bson {
        return if (field == "_id") {
            when (value) {
                is String -> if (ObjectId.isValid(value)) {
                    Filters.or(Filters.eq(field, ObjectId(value)), Filters.eq(field, value))
                } else {
                    Filters.eq(field, value)
                }
                is ObjectId -> Filters.eq(field, value)
                else -> Filters.eq(field, value)
            }
        } else {
            Filters.eq(field, value)
        }
    }

    fun createSetUpdate(field: String, value: Any): Bson = Updates.set(field, value)

    fun createUnsetUpdate(field: String): Bson = Updates.unset(field)

    fun createSetUpdates(updates: Map<String, Any>): Bson =
        Updates.combine(updates.map { (field, value) -> Updates.set(field, value) })
}
