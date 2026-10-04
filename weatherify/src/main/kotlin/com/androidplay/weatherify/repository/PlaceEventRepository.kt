package com.androidplay.weatherify.repository

import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.core.common.Result

/**
 * Repository for per-account place calendar events.
 */
interface PlaceEventRepository {
    suspend fun create(event: PlaceEvent): Result<PlaceEvent>
    /**
     * Events for [email] whose stored coordinates are within about 1 km of [lat]/[lon].
     * Soonest [PlaceEvent.startsAt] first.
     */
    suspend fun listByUserAndPlace(
        email: String,
        lat: Double,
        lon: Double
    ): Result<List<PlaceEvent>>
}
