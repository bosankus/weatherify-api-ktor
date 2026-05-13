package com.androidplay.weatherify.repository.mongo

import com.androidplay.weatherify.domain.SavedLocation
import com.androidplay.weatherify.db.WeatherifyDb
import com.androidplay.core.common.Result
import com.androidplay.weatherify.repository.SavedLocationRepository
import kotlinx.coroutines.flow.toList


/**
 * Implementation of SavedLocationRepository that uses MongoDB for data storage.
 */
class SavedLocationRepositoryImpl(private val databaseModule: WeatherifyDb) : SavedLocationRepository {

    override suspend fun saveLocation(location: SavedLocation): Result<Boolean> {
        return try {
            val result = databaseModule.getSavedLocationsCollection().insertOne(location)
            Result.success(result.wasAcknowledged())
        } catch (e: Exception) {
            Result.error("Failed to save location: ${e.message}", e)
        }
    }

    override suspend fun getLocationsByUser(email: String): Result<List<SavedLocation>> {
        return try {
            val query = databaseModule.createQuery("userEmail", email)
            val locations = databaseModule.getSavedLocationsCollection().find(query).toList()
            Result.success(locations)
        } catch (e: Exception) {
            Result.error("Failed to get locations for user: ${e.message}", e)
        }
    }

    override suspend fun deleteLocation(id: String, email: String): Result<Boolean> {
        return try {
            val query = databaseModule.createQuery(
                mapOf(
                    "_id" to org.bson.types.ObjectId(id),
                    "userEmail" to email
                )
            )
            val result = databaseModule.getSavedLocationsCollection().deleteOne(query)
            Result.success(result.wasAcknowledged() && result.deletedCount > 0)
        } catch (e: Exception) {
            Result.error("Failed to delete location: ${e.message}", e)
        }
    }

    override suspend fun locationExists(email: String, name: String, city: String, state: String, country: String): Result<Boolean> {
        return try {
            val query = databaseModule.createQuery(
                mapOf(
                    "userEmail" to email,
                    "name" to name,
                    "city" to city,
                    "state" to state,
                    "country" to country
                )
            )
            val count = databaseModule.getSavedLocationsCollection().countDocuments(query)
            Result.success(count > 0)
        } catch (e: Exception) {
            Result.error("Failed to check location existence: ${e.message}", e)
        }
    }
}
