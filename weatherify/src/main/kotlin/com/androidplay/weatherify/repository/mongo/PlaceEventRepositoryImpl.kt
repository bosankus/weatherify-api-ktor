package com.androidplay.weatherify.repository.mongo

import com.androidplay.core.common.Result
import com.androidplay.weatherify.db.WeatherifyDb
import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.weatherify.domain.PlaceProximity
import com.androidplay.weatherify.repository.PlaceEventRepository
import com.mongodb.client.model.Filters
import kotlinx.coroutines.flow.toList

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
                lonFilter
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
}
