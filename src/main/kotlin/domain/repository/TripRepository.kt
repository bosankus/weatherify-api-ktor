package domain.repository

import bose.ankush.data.model.Trip
import domain.model.Result

/**
 * Repository interface for managing user trips for the Travel Concierge feature.
 */
interface TripRepository {
    suspend fun saveTrip(trip: Trip): Result<Boolean>
    suspend fun getTripsByUser(email: String): Result<List<Trip>>
    suspend fun getActiveTrip(email: String): Result<Trip?>
    suspend fun deleteTrip(id: String, email: String): Result<Boolean>
}
