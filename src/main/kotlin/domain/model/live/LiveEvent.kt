package domain.model.live

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Domain events pushed on a location topic.
 *
 * These are deliberately *semantic*, not raw upstream payloads. The server diffs each new
 * upstream snapshot against the previous one and emits what changed and why it matters.
 * Two reasons:
 *   1. bandwidth — a full UnifiedWeatherResponse is ~8-20 KB; a delta is ~120 bytes
 *   2. the client shouldn't have to re-derive "did this get worse?" on every frame
 *
 * Nested under ServerMessage.Event, so it uses its own "kind" discriminator to avoid
 * colliding with the envelope's "type".
 */
@Serializable
@JsonClassDiscriminator("kind")
@OptIn(ExperimentalSerializationApi::class)
sealed class LiveEvent {

    /**
     * Full current state for a topic. Sent once immediately after subscribing, and again
     * whenever the server decides the client's delta chain cannot be trusted (see TopicAck.gap).
     * Everything else in this file is a delta relative to the most recent Snapshot.
     */
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        val conditions: Conditions,
        val aqi: AirQualitySample? = null,
        val activeAlerts: List<AlertInfo> = emptyList()
    ) : LiveEvent()

    /** One or more current-conditions fields moved past the reporting threshold. */
    @Serializable
    @SerialName("conditions.update")
    data class ConditionsUpdate(
        val conditions: Conditions,
        /** Names of the fields that actually changed, so the UI can animate only those. */
        val changed: List<String> = emptyList()
    ) : LiveEvent()

    /** A severe weather alert became active for this location. This is the Tier-1 event. */
    @Serializable
    @SerialName("alert.issued")
    data class AlertIssued(val alert: AlertInfo) : LiveEvent()

    /** A previously active alert expired or was withdrawn upstream. */
    @Serializable
    @SerialName("alert.cleared")
    data class AlertCleared(val alertId: String, val event: String) : LiveEvent()

    /** Air quality moved into a different AQI band (1..5). Premium channel. */
    @Serializable
    @SerialName("aqi.threshold_crossed")
    data class AqiThresholdCrossed(
        val sample: AirQualitySample,
        val previousBand: Int,
        val band: Int,
        val worsening: Boolean
    ) : LiveEvent()

    /** Wind gust jumped sharply since the last sample. */
    @Serializable
    @SerialName("wind.gust_spike")
    data class WindGustSpike(
        val gustMs: Double,
        val previousGustMs: Double,
        val deltaMs: Double
    ) : LiveEvent()

    /** The next hourly bucket flipped to a precipitating condition. */
    @Serializable
    @SerialName("rain.starting_soon")
    data class RainStartingSoon(
        val startsAtEpochSeconds: Long,
        val minutesAway: Int,
        val description: String
    ) : LiveEvent()

    /** UV index crossed into the "high" band. */
    @Serializable
    @SerialName("uv.high")
    data class UvHigh(val uvi: Double, val previousUvi: Double) : LiveEvent()
}

/** Current conditions, trimmed to what the live UI actually renders. */
@Serializable
data class Conditions(
    /** Upstream observation timestamp, epoch seconds. Not the same as the frame's ts. */
    val observedAt: Long,
    val tempC: Double?,
    val feelsLikeC: Double?,
    val humidity: Int?,
    val pressure: Int?,
    val windSpeedMs: Double?,
    val windGustMs: Double?,
    val uvi: Double?,
    val clouds: Int?,
    val summary: String?,
    val icon: String?
)

@Serializable
data class AirQualitySample(
    val band: Int,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null,
    val no2: Double? = null
)

@Serializable
data class AlertInfo(
    /**
     * Stable synthetic id derived from (sender, event, start). Upstream gives no id, and the
     * client needs one to correlate an issued event with its later cleared event.
     */
    val id: String,
    val event: String,
    val senderName: String? = null,
    val description: String? = null,
    val startsAt: Long? = null,
    val endsAt: Long? = null
)

/** Channel names used in Subscribe.channels and Hello.allowedChannels. */
object LiveChannel {
    const val CONDITIONS = "conditions"
    const val ALERTS = "alerts"
    const val AQI = "aqi"

    val ALL = listOf(CONDITIONS, ALERTS, AQI)

    /** Which channel a given event belongs to, for per-connection filtering. */
    fun of(event: LiveEvent): String = when (event) {
        is LiveEvent.Snapshot -> CONDITIONS
        is LiveEvent.ConditionsUpdate -> CONDITIONS
        is LiveEvent.WindGustSpike -> CONDITIONS
        is LiveEvent.RainStartingSoon -> CONDITIONS
        is LiveEvent.UvHigh -> CONDITIONS
        is LiveEvent.AlertIssued -> ALERTS
        is LiveEvent.AlertCleared -> ALERTS
        is LiveEvent.AqiThresholdCrossed -> AQI
    }
}
