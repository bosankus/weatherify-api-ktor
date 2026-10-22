package com.androidplay.weatherify.repository

import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.weatherify.domain.PlaceEventSync
import com.androidplay.core.common.Result
import org.bson.types.ObjectId

/**
 * Repository for per-account place calendar events.
 */
interface PlaceEventRepository {
    suspend fun create(event: PlaceEvent): Result<PlaceEvent>
    /**
     * Live (not soft-deleted) events for [email] whose stored coordinates are within about
     * 1 km of [lat]/[lon]. Soonest [PlaceEvent.startsAt] first.
     */
    suspend fun listByUserAndPlace(
        email: String,
        lat: Double,
        lon: Double
    ): Result<List<PlaceEvent>>

    /** Any user's event with this client-generated id (callers enforce ownership). */
    suspend fun findByClientId(clientId: String): Result<PlaceEvent?>

    /** Any user's event with this Mongo _id (callers enforce ownership). */
    suspend fun findById(id: ObjectId): Result<PlaceEvent?>

    /**
     * Replaces [current] with [replacement] only if the stored row still has the same
     * updatedAt and deletedAt as [current] (compare-and-set). Success(false) means it changed.
     */
    suspend fun replaceIfUnchanged(current: PlaceEvent, replacement: PlaceEvent): Result<Boolean>

    /**
     * Up to [limit] of [email]'s events (tombstones included) sorted by [PlaceEventSync.ORDER]
     * that come strictly after [cursor]; all of them when [cursor] is null.
     */
    suspend fun listChangedSince(
        email: String,
        cursor: PlaceEventSync.Cursor?,
        limit: Int
    ): Result<List<PlaceEvent>>
}
