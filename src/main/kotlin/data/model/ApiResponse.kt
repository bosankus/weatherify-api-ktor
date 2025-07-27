package bose.ankush.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * Custom serializer for Unit type
 */
object UnitSerializer : KSerializer<Unit> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Unit", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Unit) {
        // Serialize Unit as an empty JSON object
        encoder.encodeString("{}")
    }

    override fun deserialize(decoder: Decoder) {
        // Ignore the actual value
        decoder.decodeString()
        return
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class ApiResponse<T>(
    var status: Boolean,
    var message: String,
    var data: T? = null
)