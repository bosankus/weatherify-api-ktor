package bose.ankush.data.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.time.Instant

@Serializable
data class Trip(
    @SerialName("_id")
    @Contextual
    val id: ObjectId = ObjectId(),
    val userEmail: String,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLon: Double,
    val startDate: Long, // Timestamp
    val endDate: Long,   // Timestamp
    val createdAt: String = Instant.now().toString()
)

@Serializable
data class TripIntelligence(
    val packing: PackingAdvice,
    val health: HealthAdvice,
    val jetLag: JetLagAdvice
)

@Serializable
data class PackingAdvice(
    val header: String,
    val advice: String,
    val items: List<String>
)

@Serializable
data class HealthAdvice(
    val aqiAlert: String,
    val advice: String
)

@Serializable
data class JetLagAdvice(
    val strategy: String,
    val sunlightWindow: String
)
