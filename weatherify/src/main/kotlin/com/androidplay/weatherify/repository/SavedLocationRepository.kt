package com.androidplay.weatherify.repository

import com.androidplay.weatherify.domain.SavedLocation
import com.androidplay.core.common.Result

/**
 * Repository interface for managing saved locations.
 */
interface SavedLocationRepository {
    suspend fun saveLocation(location: SavedLocation): Result<Boolean>
    suspend fun getLocationsByUser(email: String): Result<List<SavedLocation>>
    suspend fun deleteLocation(id: String, email: String): Result<Boolean>
    suspend fun locationExists(email: String, name: String, city: String, state: String, country: String): Result<Boolean>
}
