package domain.repository

import bose.ankush.data.model.SavedLocation
import domain.model.Result

/**
 * Repository interface for managing saved locations.
 */
interface SavedLocationRepository {
    suspend fun saveLocation(location: SavedLocation): Result<Boolean>
    suspend fun getLocationsByUser(email: String): Result<List<SavedLocation>>
    suspend fun deleteLocation(id: String, email: String): Result<Boolean>
}
