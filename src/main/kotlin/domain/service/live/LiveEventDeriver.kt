package domain.service.live

import domain.model.live.LiveEvent
import kotlin.math.abs

/**
 * Diffs two consecutive [LocationSnapshot]s and produces the semantic events worth pushing.
 *
 * Everything here is a noise filter. Upstream numbers jitter constantly; without thresholds the
 * server would emit a frame on every poll for every location, waking every radio on every device
 * to deliver "the temperature moved 0.01 degrees". Deciding what is *worth a push* is a product
 * decision that belongs on the server, precisely because it can be changed without an app release.
 */
object LiveEventDeriver {

    /** Minimum change before a conditions.update is emitted at all. */
    private const val TEMP_EPSILON_C = 0.2
    private const val FEELS_LIKE_EPSILON_C = 0.3
    private const val WIND_EPSILON_MS = 0.5
    private const val HUMIDITY_EPSILON_PCT = 2
    private const val UVI_EPSILON = 0.3

    /** A gust jumping by this much within one interval is worth its own event. */
    private const val GUST_SPIKE_MS = 4.0

    /** UV index band considered "high" by WHO. */
    private const val UV_HIGH = 6.0

    /** Only announce rain if it starts within this window. */
    private const val RAIN_HORIZON_MINUTES = 90

    /**
     * @param previous last snapshot pushed for this topic, or null on the first poll
     * @return events in the order they should be delivered; empty when nothing meaningful moved
     */
    fun derive(previous: LocationSnapshot?, current: LocationSnapshot): List<LiveEvent> {
        // First observation for this topic: the client has no baseline, so send everything.
        if (previous == null) {
            return listOf(
                LiveEvent.Snapshot(
                    conditions = current.conditions,
                    aqi = current.aqi,
                    activeAlerts = current.alerts
                )
            )
        }

        val events = mutableListOf<LiveEvent>()

        // --- alerts: highest priority, emitted first so a client that drops frames under
        // --- backpressure still keeps the one that matters (see LiveHub's drop policy).
        val previousIds = previous.alerts.associateBy { it.id }
        val currentIds = current.alerts.associateBy { it.id }

        current.alerts.filter { it.id !in previousIds }
            .forEach { events += LiveEvent.AlertIssued(it) }

        previous.alerts.filter { it.id !in currentIds }
            .forEach { events += LiveEvent.AlertCleared(alertId = it.id, event = it.event) }

        // --- wind gust spike
        val prevGust = previous.conditions.windGustMs
        val curGust = current.conditions.windGustMs
        if (prevGust != null && curGust != null && curGust - prevGust >= GUST_SPIKE_MS) {
            events += LiveEvent.WindGustSpike(
                gustMs = curGust,
                previousGustMs = prevGust,
                deltaMs = (curGust - prevGust)
            )
        }

        // --- UV crossing into the high band (edge-triggered, not level-triggered:
        // --- we only fire on the crossing, otherwise it would repeat every poll all afternoon)
        val prevUvi = previous.conditions.uvi
        val curUvi = current.conditions.uvi
        if (prevUvi != null && curUvi != null && prevUvi < UV_HIGH && curUvi >= UV_HIGH) {
            events += LiveEvent.UvHigh(uvi = curUvi, previousUvi = prevUvi)
        }

        // --- AQI band change (also edge-triggered)
        val prevBand = previous.aqi?.band
        val curSample = current.aqi
        if (prevBand != null && curSample != null && curSample.band != prevBand) {
            events += LiveEvent.AqiThresholdCrossed(
                sample = curSample,
                previousBand = prevBand,
                band = curSample.band,
                worsening = curSample.band > prevBand
            )
        }

        // --- rain starting soon (edge-triggered on the start time changing)
        val curRain = current.nextPrecipitation
        val prevRain = previous.nextPrecipitation
        if (curRain != null && curRain.atEpochSeconds != prevRain?.atEpochSeconds) {
            val minutesAway = ((curRain.atEpochSeconds - current.conditions.observedAt) / 60).toInt()
            if (minutesAway in 0..RAIN_HORIZON_MINUTES) {
                events += LiveEvent.RainStartingSoon(
                    startsAtEpochSeconds = curRain.atEpochSeconds,
                    minutesAway = minutesAway,
                    description = curRain.description
                )
            }
        }

        // --- ordinary conditions drift, last: least urgent, largest payload
        val changed = changedFields(previous.conditions, current.conditions)
        if (changed.isNotEmpty()) {
            events += LiveEvent.ConditionsUpdate(
                conditions = current.conditions,
                changed = changed
            )
        }

        return events
    }

    private fun changedFields(
        previous: domain.model.live.Conditions,
        current: domain.model.live.Conditions
    ): List<String> {
        val changed = mutableListOf<String>()

        if (movedBy(previous.tempC, current.tempC, TEMP_EPSILON_C)) changed += "tempC"
        if (movedBy(previous.feelsLikeC, current.feelsLikeC, FEELS_LIKE_EPSILON_C)) changed += "feelsLikeC"
        if (movedBy(previous.windSpeedMs, current.windSpeedMs, WIND_EPSILON_MS)) changed += "windSpeedMs"
        if (movedBy(previous.windGustMs, current.windGustMs, WIND_EPSILON_MS)) changed += "windGustMs"
        if (movedBy(previous.uvi, current.uvi, UVI_EPSILON)) changed += "uvi"
        if (previous.humidity != null && current.humidity != null &&
            abs(previous.humidity - current.humidity) >= HUMIDITY_EPSILON_PCT
        ) changed += "humidity"
        if (previous.icon != current.icon) changed += "icon"
        if (previous.summary != current.summary) changed += "summary"

        return changed
    }

    private fun movedBy(a: Double?, b: Double?, epsilon: Double): Boolean =
        a != null && b != null && abs(a - b) >= epsilon
}
