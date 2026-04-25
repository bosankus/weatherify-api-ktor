package bose.ankush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.time.Instant

/**
 * Model representing a location saved by a user.
 */
@Serializable
data class SavedLocation(
    @SerialName("_id")
    @Serializable(with = FlexibleObjectIdSerializer::class)
    val id: ObjectId = ObjectId(),
    val userEmail: String,
    val name: String,
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val lat: Double,
    val lon: Double,
    val createdAt: String = Instant.now().toString()
)

/**
 * Filtered place result returned from Nominatim search.
 */
@Serializable
data class PlaceSearchResult(
    val name: String,
    val city: String,
    val state: String,
    val country: String,
    val lat: String,
    val lon: String
)
