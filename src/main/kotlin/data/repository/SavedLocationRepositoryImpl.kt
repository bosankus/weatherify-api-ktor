package data.repository

import bose.ankush.data.model.SavedLocation
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.SavedLocationRepository
import kotlinx.coroutines.flow.toList
import util.Constants

/**
 * Implementation of SavedLocationRepository that uses MongoDB for data storage.
 */
class SavedLocationRepositoryImpl(private val databaseModule: DatabaseModule) : SavedLocationRepository {

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
            val query = databaseModule.createQuery(Constants.Database.EMAIL_FIELD, email)
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
                    Constants.Database.ID_FIELD to org.bson.types.ObjectId(id),
                    Constants.Database.EMAIL_FIELD to email
                )
            )
            val result = databaseModule.getSavedLocationsCollection().deleteOne(query)
            Result.success(result.wasAcknowledged() && result.deletedCount > 0)
        } catch (e: Exception) {
            Result.error("Failed to delete location: ${e.message}", e)
        }
    }
}
