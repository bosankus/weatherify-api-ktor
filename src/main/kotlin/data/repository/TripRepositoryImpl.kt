package data.repository

import bose.ankush.data.model.Trip
import data.source.DatabaseModule
import domain.model.Result
import domain.repository.TripRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import util.Constants
import java.time.Instant

/**
 * Implementation of TripRepository that uses MongoDB for data storage.
 */
class TripRepositoryImpl(private val databaseModule: DatabaseModule) : TripRepository {

    override suspend fun saveTrip(trip: Trip): Result<Boolean> {
        return try {
            val result = databaseModule.getTripsCollection().insertOne(trip)
            Result.success(result.wasAcknowledged())
        } catch (e: Exception) {
            Result.error("Failed to save trip: ${e.message}", e)
        }
    }

    override suspend fun getTripsByUser(email: String): Result<List<Trip>> {
        return try {
            val query = databaseModule.createQuery(Constants.Database.EMAIL_FIELD, email)
            val trips = databaseModule.getTripsCollection().find(query).toList()
            Result.success(trips)
        } catch (e: Exception) {
            Result.error("Failed to get trips for user: ${e.message}", e)
        }
    }

    override suspend fun getActiveTrip(email: String): Result<Trip?> {
        return try {
            val now = Instant.now().toEpochMilli()
            val query = databaseModule.createQuery(
                mapOf(
                    Constants.Database.EMAIL_FIELD to email,
                    // Active trip: starts in the future or is currently ongoing (ends in the future)
                    "endDate" to Document("${"$"}gte", now)
                )
            )
            // Sort by startDate to get the nearest one first
            val trip = databaseModule.getTripsCollection().find(query)
                .sort(Document("startDate", 1))
                .firstOrNull()
            Result.success(trip)
        } catch (e: Exception) {
            Result.error("Failed to get active trip: ${e.message}", e)
        }
    }

    override suspend fun deleteTrip(id: String, email: String): Result<Boolean> {
        return try {
            val query = databaseModule.createQuery(
                mapOf(
                    Constants.Database.ID_FIELD to org.bson.types.ObjectId(id),
                    Constants.Database.EMAIL_FIELD to email
                )
            )
            val result = databaseModule.getTripsCollection().deleteOne(query)
            Result.success(result.wasAcknowledged() && result.deletedCount > 0)
        } catch (e: Exception) {
            Result.error("Failed to delete trip: ${e.message}", e)
        }
    }
}
