package bose.ankush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import org.bson.types.ObjectId
import java.time.Instant

/**
 * Model representing a note created by an admin user.
 */
@Serializable
data class Note(
    @SerialName("_id")
    @Contextual
    val id: ObjectId = ObjectId(),
    val userEmail: String,
    val content: String,
    val contentFormat: String = "richtext-json",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString()
)
