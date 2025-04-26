package bose.ankush.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackResponse<T>(
    var status: Boolean,
    var message: String,
    var data: T
)
