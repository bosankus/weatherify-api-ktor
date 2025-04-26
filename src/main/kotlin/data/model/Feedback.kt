package bose.ankush.data.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Feedback(
    @SerialName("_id")
    @Contextual var id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val deviceOs: String,
    val feedbackTitle: String,
    val feedbackDescription: String,
    val timestamp: String = (System.currentTimeMillis() / 1000).toString()
)
