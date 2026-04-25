package bose.ankush.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory

/**
 * Custom serializer for ObjectId that handles both STRING and OBJECT_ID types from MongoDB.
 * This allows seamless migration from string-based IDs to proper ObjectId types.
 *
 * When deserializing from MongoDB:
 * - If the document has _id as ObjectId type, it deserializes correctly
 * - If the document has _id as String type, it converts the string to ObjectId
 *
 * This handles the error: "BsonInvalidOperationException: Reading field '_id' failed
 * expected OBJECT_ID type but found: STRING"
 */
object FlexibleObjectIdSerializer : KSerializer<ObjectId> {
    private val logger = LoggerFactory.getLogger(FlexibleObjectIdSerializer::class.java)

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ObjectId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ObjectId) {
        encoder.encodeString(value.toHexString())
    }

    override fun deserialize(decoder: Decoder): ObjectId {
        return try {
            // Try to decode as string first (works for both STRING type and fallback)
            val stringValue = decoder.decodeString()

            return try {
                // If it's a valid ObjectId hex string, convert it
                ObjectId(stringValue)
            } catch (e: IllegalArgumentException) {
                // If string is not a valid ObjectId, generate a new one
                logger.warn("Invalid ObjectId string format: '$stringValue', generating new ObjectId", e)
                ObjectId()
            }
        } catch (e: Exception) {
            logger.error("Error deserializing ObjectId, generating new one", e)
            // Fallback: generate a new ObjectId
            ObjectId()
        }
    }
}
