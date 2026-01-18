package bose.ankush.data.model

import kotlinx.serialization.Contextual
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
    @Contextual
    val id: ObjectId = ObjectId(),
    val userEmail: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val createdAt: String = Instant.now().toString()
)
