package com.androidplay.weatherify.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import org.bson.types.ObjectId
import java.time.Instant

/**
 * Calendar event for a signed-in user at a specific place (Wander home).
 * Place is supplied by the client (name + lat/lon); never hardcoded.
 *
 * Offline-first sync fields:
 * - [clientId] is an optional client-generated UUID (lowercase). When present it is the public id.
 * - [updatedAt] is written on every create/update/delete in [PlaceEventSync.format] (fixed-width,
 *   millisecond precision, so string order equals time order). Rows written before sync existed
 *   have no [updatedAt]; readers treat it as [createdAt] (see [PlaceEventSync.effectiveUpdatedAt]).
 * - [deletedAt] marks a soft-deleted row (tombstone) that sync still returns.
 */
@Serializable
data class PlaceEvent(
    @SerialName("_id")
    @Contextual
    val id: ObjectId = ObjectId(),
    val userEmail: String,
    val placeName: String = "",
    val lat: Double,
    val lon: Double,
    val title: String,
    /** ISO-8601 instant when the event starts. */
    val startsAt: String,
    val note: String? = null,
    val createdAt: String = Instant.now().toString(),
    val clientId: String? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null
) {
    /** Id exposed to clients: the client-generated UUID when present, else the ObjectId hex. */
    val publicId: String get() = clientId ?: id.toHexString()
}
