package com.androidplay.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.types.ObjectId

/**
 * Custom serializer for ObjectId that handles both STRING and OBJECT_ID types from MongoDB.
 * Supports migration from string-based IDs to proper ObjectId types.
 *
 * Throws [SerializationException] on malformed input rather than silently generating a
 * new ObjectId, so data corruption is surfaced immediately rather than hidden.
 */
object FlexibleObjectIdSerializer : KSerializer<ObjectId> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ObjectId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ObjectId) {
        encoder.encodeString(value.toHexString())
    }

    override fun deserialize(decoder: Decoder): ObjectId {
        val raw = try {
            decoder.decodeString()
        } catch (e: Exception) {
            throw SerializationException("Failed to read ObjectId field as a string", e)
        }
        return try {
            ObjectId(raw)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("'$raw' is not a valid ObjectId hex string", e)
        }
    }
}
