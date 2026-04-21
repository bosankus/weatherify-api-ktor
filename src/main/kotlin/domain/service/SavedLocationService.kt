package domain.service

import bose.ankush.data.model.PlaceSearchResult
import bose.ankush.data.model.SavedLocation
import domain.model.Result
import domain.model.SubscriptionFeatureResolver
import domain.repository.SavedLocationRepository
import domain.repository.UserRepository

class SavedLocationService(
    private val repository: SavedLocationRepository,
    private val userRepository: UserRepository,
    private val nominatimService: NominatimService
) {

    suspend fun isPremiumActive(email: String): Boolean {
        val user = userRepository.findUserByEmail(email).getOrNull() ?: return false
        return SubscriptionFeatureResolver.resolveFeatures(user).isNotEmpty()
    }

    suspend fun searchPlace(query: String): Result<List<PlaceSearchResult>> =
        nominatimService.searchPlace(query)

    suspend fun saveLocation(
        email: String,
        name: String,
        city: String,
        state: String,
        country: String,
        lat: Double,
        lon: Double
    ): Result<Boolean> {
        val location = SavedLocation(
            userEmail = email,
            name = name,
            city = city,
            state = state,
            country = country,
            lat = lat,
            lon = lon
        )
        return repository.saveLocation(location)
    }

    suspend fun getSavedLocations(email: String): Result<List<SavedLocation>> =
        repository.getLocationsByUser(email)

    suspend fun deleteSavedLocation(id: String, email: String): Result<Boolean> =
        repository.deleteLocation(id, email)
}
