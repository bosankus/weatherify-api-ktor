package com.androidplay.weatherify.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import org.bson.types.ObjectId
import java.time.Instant

/**
 * Calendar event for a signed-in user at a specific place (Wander home).
 * Place is supplied by the client (name + lat/lon); never hardcoded.
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
    val createdAt: String = Instant.now().toString()
)
