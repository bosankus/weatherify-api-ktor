package domain.service

import bose.ankush.data.model.SavedLocation
import domain.model.Result
import domain.repository.SavedLocationRepository

/**
 * Service class for managing saved locations.
 */
class SavedLocationService(private val repository: SavedLocationRepository) {

    suspend fun saveLocation(email: String, name: String, lat: Double, lon: Double): Result<Boolean> {
        val location = SavedLocation(
            userEmail = email,
            name = name,
            lat = lat,
            lon = lon
        )
        return repository.saveLocation(location)
    }

    suspend fun getSavedLocations(email: String): Result<List<SavedLocation>> {
        return repository.getLocationsByUser(email)
    }

    suspend fun deleteSavedLocation(id: String, email: String): Result<Boolean> {
        return repository.deleteLocation(id, email)
    }
}
