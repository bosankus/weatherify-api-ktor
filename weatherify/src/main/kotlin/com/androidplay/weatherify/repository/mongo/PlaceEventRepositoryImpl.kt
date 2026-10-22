package com.androidplay.weatherify.repository.mongo

import com.androidplay.core.common.Result
import com.androidplay.weatherify.db.WeatherifyDb
import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.weatherify.domain.PlaceEventSync
import com.androidplay.weatherify.domain.PlaceProximity
import com.androidplay.weatherify.repository.PlaceEventRepository
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.BsonType
import org.bson.types.ObjectId

/**
 * MongoDB implementation of [PlaceEventRepository].
 * Listing matches the same account within [PlaceProximity.MATCH_RADIUS_METERS]
 * (about 1 km), not exact double equality, so a GPS drift still finds the place.
 */
class PlaceEventRepositoryImpl(private val databaseModule: WeatherifyDb) : PlaceEventRepository {

    override suspend fun create(event: PlaceEvent): Result<PlaceEvent> {
        return try {
            val result = databaseModule.getPlaceEventsCollection().insertOne(event)
            if (result.wasAcknowledged()) Result.success(event)
            else Result.error("Failed to create place event")
        } catch (e: Exception) {
            Result.error("Failed to create place event: ${e.message}", e)
        }
    }

    override suspend fun listByUserAndPlace(
        email: String,
        lat: Double,
        lon: Double
    ): Result<List<PlaceEvent>> {
        return try {
            val box = PlaceProximity.boundingBox(lat, lon)
            val lonFilter = if (box.crossesDateline) {
                Filters.or(
                    Filters.gte("lon", box.minLon),
                    Filters.lte("lon", box.maxLon)
                )
            } else {
                Filters.and(
                    Filters.gte("lon", box.minLon),
                    Filters.lte("lon", box.maxLon)
                )
            }
            val filter = Filters.and(
                Filters.eq("userEmail", email),
                Filters.gte("lat", box.minLat),
                Filters.lte("lat", box.maxLat),
                lonFilter,
                // Soft-deleted rows stay for sync but never show in the place list.
                Filters.eq("deletedAt", null)
            )
            val events = databaseModule.getPlaceEventsCollection()
                .find(filter)
                .toList()
                .filter { PlaceProximity.withinMatchRadius(lat, lon, it.lat, it.lon) }
                .sortedBy { it.startsAt }
            Result.success(events)
        } catch (e: Exception) {
            Result.error("Failed to list place events: ${e.message}", e)
        }
    }

    override suspend fun findByClientId(clientId: String): Result<PlaceEvent?> = try {
        Result.success(
            databaseModule.getPlaceEventsCollection().find(Filters.eq("clientId", clientId)).firstOrNull()
        )
    } catch (e: Exception) {
        Result.error("Failed to find place event: ${e.message}", e)
    }

    override suspend fun findById(id: ObjectId): Result<PlaceEvent?> = try {
        Result.success(
            databaseModule.getPlaceEventsCollection().find(Filters.eq("_id", id)).firstOrNull()
        )
    } catch (e: Exception) {
        Result.error("Failed to find place event: ${e.message}", e)
    }

    override suspend fun replaceIfUnchanged(current: PlaceEvent, replacement: PlaceEvent): Result<Boolean> = try {
        // eq(field, null) matches both a missing field (legacy rows) and an explicit null.
        val filter = Filters.and(
            Filters.eq("_id", current.id),
            Filters.eq("userEmail", current.userEmail),
            Filters.eq("updatedAt", current.updatedAt),
            Filters.eq("deletedAt", current.deletedAt)
        )
        val result = databaseModule.getPlaceEventsCollection().replaceOne(filter, replacement)
        Result.success(result.matchedCount == 1L)
    } catch (e: Exception) {
        Result.error("Failed to update place event: ${e.message}", e)
    }

    override suspend fun listChangedSince(
        email: String,
        cursor: PlaceEventSync.Cursor?,
        limit: Int
    ): Result<List<PlaceEvent>> = try {
        val collection = databaseModule.getPlaceEventsCollection()
        val byUser = Filters.eq("userEmail", email)

        // Rows written since sync existed: updatedAt is a fixed-width ms string, so string
        // comparison is time comparison and the (userEmail, updatedAt, _id) index serves it.
        val position = when {
            cursor == null -> Filters.type("updatedAt", BsonType.STRING)
            cursor.afterId == null -> Filters.gt("updatedAt", PlaceEventSync.format(cursor.after))
            else -> {
                val at = PlaceEventSync.format(cursor.after)
                Filters.or(
                    Filters.gt("updatedAt", at),
                    Filters.and(Filters.eq("updatedAt", at), Filters.gt("_id", cursor.afterId))
                )
            }
        }
        val fresh = collection.find(Filters.and(byUser, position))
            .sort(Sorts.ascending("updatedAt", "_id"))
            .limit(limit)
            .toList()

        // Legacy rows (no updatedAt): effective updatedAt is createdAt, whose stored string has
        // variable precision, so compare parsed instants in memory. Each legacy row leaves this set
        // the first time it is updated or deleted.
        val legacy = collection.find(Filters.and(byUser, Filters.eq("updatedAt", null)))
            .toList()
            .filter { PlaceEventSync.isAfter(it, cursor) }

        Result.success((fresh + legacy).sortedWith(PlaceEventSync.ORDER).take(limit))
    } catch (e: Exception) {
        Result.error("Failed to list place event changes: ${e.message}", e)
    }
}
